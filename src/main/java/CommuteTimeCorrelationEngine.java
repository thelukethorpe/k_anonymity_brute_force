import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class CommuteTimeCorrelationEngine {
  private static class PostcodeHeuristicCommuteTimePair {
    private final double postcodeHeuristic;
    private final double commuteTime;

    private PostcodeHeuristicCommuteTimePair(double postcodeHeuristic, double commuteTime) {
      this.postcodeHeuristic = postcodeHeuristic;
      this.commuteTime = commuteTime;
    }

    @Override
    public String toString() {
      return postcodeHeuristic + ", " + commuteTime;
    }
  }

  private static class LatitudeLongitudePair {
    private final double latitude;
    private final double longitude;

    private LatitudeLongitudePair(double latitude, double longitude) {
      this.latitude = latitude;
      this.longitude = longitude;
    }

    public double getLatitude() {
      return latitude;
    }

    public double getLongitude() {
      return longitude;
    }
  }

  private static final int RADIUS_OF_THE_EARTH_KM = 6371;

  private static double sinSquared(double x) {
    double sin = Math.sin(x);
    return sin * sin;
  }

  private static double computeHaversineDistance(
      LatitudeLongitudePair coordinates1, LatitudeLongitudePair coordinates2) {
    double degreesOfLatitude = degreesToRadians(coordinates2.latitude - coordinates1.latitude);
    double degreesOfLongitude = degreesToRadians(coordinates2.longitude - coordinates1.longitude);
    double a =
        sinSquared(degreesOfLatitude / 2.0)
            + Math.cos(degreesToRadians(coordinates1.latitude))
                * Math.cos(degreesToRadians(coordinates2.latitude))
                * sinSquared(degreesOfLongitude / 2.0);
    return RADIUS_OF_THE_EARTH_KM * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
  }

  private static double computePostcodeHeuristicInKm(
      String postcode1,
      String postcode2,
      Map<String, LatitudeLongitudePair> postcodeToCoordinateMap) {
    return computeHaversineDistance(
        postcodeToCoordinateMap.get(postcode1), postcodeToCoordinateMap.get(postcode2));
  }

  private static double degreesToRadians(double degrees) {
    return degrees * (Math.PI / 180.0);
  }

  // Assumes x1 < x < x2 and y1 < y2.
  private static double interpolate(double x, double x1, double x2, double y1, double y2) {
    double m = (y2 - y1) / (x2 - x1);
    double c = y1 - m * x1;
    return m * x + c;
  }

  private static void interpolateMissingPostcodeMappings(
      TreeMap<String, LatitudeLongitudePair> postcodeToCoordinateMap) {
    for (int i = 0; i < 100000; i++) {
      String postcode = String.format("%05d", i);
      if (postcodeToCoordinateMap.containsKey(postcode)) {
        continue;
      }
      Entry<String, LatitudeLongitudePair> floor = postcodeToCoordinateMap.floorEntry(postcode);
      Entry<String, LatitudeLongitudePair> ceiling = postcodeToCoordinateMap.ceilingEntry(postcode);
      if (floor == null) {
        postcodeToCoordinateMap.put(postcode, ceiling.getValue());
      } else if (ceiling == null) {
        postcodeToCoordinateMap.put(postcode, floor.getValue());
      } else {
        double floorPostcode = Double.parseDouble(floor.getKey());
        double ceilingPostcode = Double.parseDouble(ceiling.getKey());
        double interpolatedLatitude =
            interpolate(
                i,
                floorPostcode,
                ceilingPostcode,
                floor.getValue().latitude,
                ceiling.getValue().latitude);
        double interpolatedLongitude =
            interpolate(
                i,
                floorPostcode,
                ceilingPostcode,
                floor.getValue().longitude,
                ceiling.getValue().longitude);
        postcodeToCoordinateMap.put(
            postcode, new LatitudeLongitudePair(interpolatedLatitude, interpolatedLongitude));
      }
    }
  }

  private static TreeMap<String, LatitudeLongitudePair> buildPostcodeToCoordinateMapFromCsvFile(
      File csvFile) throws IOException {
    TreeMap<String, LatitudeLongitudePair> result = new TreeMap<>();
    CsvReader csvReader = new CsvReader(csvFile);
    // Dump attributes.
    csvReader.readline();
    do {
      List<String> postcodeLatitudeLongitude = csvReader.readline();
      if (postcodeLatitudeLongitude.isEmpty()) {
        break;
      }
      String postcode = postcodeLatitudeLongitude.get(0);
      double latitude = Double.parseDouble(postcodeLatitudeLongitude.get(1));
      double longitude = Double.parseDouble(postcodeLatitudeLongitude.get(2));
      result.put(postcode, new LatitudeLongitudePair(latitude, longitude));
    } while (true);
    return result;
  }

  public static void main(String[] args) {
    String datasetCsvFilePath = args[0];
    String zipToCoordinateCsvFilePath = args[1];
    DataFrame dataFrame;
    TreeMap<String, LatitudeLongitudePair> postcodeToCoordinateMap;
    try {
      System.out.println("Loading " + datasetCsvFilePath);
      dataFrame = DataFrame.buildDataFrameFromCsvFile(new File(datasetCsvFilePath));
      System.out.println("Loading " + zipToCoordinateCsvFilePath);
      postcodeToCoordinateMap =
          buildPostcodeToCoordinateMapFromCsvFile(new File(zipToCoordinateCsvFilePath));
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
      return;
    }

    System.out.println("Interpolating missing postcode mappings.");
    interpolateMissingPostcodeMappings(postcodeToCoordinateMap);

    System.out.println("Computing heuristics for commute time.");
    dataFrame.getRecords().stream()
        .filter(
            record -> {
              Map<String, String> attributeToValueMap = record.asMap();
              return !(attributeToValueMap.get("home_postcode")
                      + attributeToValueMap.get("work_postcode"))
                  .contains("N/A");
            })
        .map(
            record -> {
              Map<String, String> attributeToValueMap = record.asMap();
              String homePostcode = attributeToValueMap.get("home_postcode");
              String workPostcode = attributeToValueMap.get("work_postcode");
              double postcodeHeuristic =
                  computePostcodeHeuristicInKm(homePostcode, workPostcode, postcodeToCoordinateMap);
              double commuteTime = Double.parseDouble(attributeToValueMap.get("commute_time"));
              return new PostcodeHeuristicCommuteTimePair(postcodeHeuristic, commuteTime);
            })
        .forEach(System.out::println);

    System.out.println("Computing ranges for truncated postcodes");
    for (int i = 1; i < 10; i++) {
      Map<String, LatitudeLongitudePair> range =
          postcodeToCoordinateMap.subMap(i + "0000", i + "9999");
      List<LatitudeLongitudePair> coordinates = new ArrayList<>(range.values());
      double longestDistance = 0.0;
      double totalDistance = 0.0;
      for (int j = 0; j < coordinates.size(); j++) {
        for (int k = 0; k < coordinates.size(); k++) {
          double distance = computeHaversineDistance(coordinates.get(j), coordinates.get(k));
          longestDistance = Math.max(longestDistance, distance);
          totalDistance += distance;
        }
      }
      double meanDistance = totalDistance / (coordinates.size() * coordinates.size());
      System.out.println(
          "Postcode range "
              + i
              + "XXXX has a longest-cut of "
              + longestDistance
              + "km and mean of "
              + meanDistance
              + "km");
      //      double minLatitude =
      //          range.values().stream()
      //              .mapToDouble(LatitudeLongitudePair::getLatitude)
      //              .min()
      //              .getAsDouble();
      //      double maxLatitude =
      //          range.values().stream()
      //              .mapToDouble(LatitudeLongitudePair::getLatitude)
      //              .max()
      //              .getAsDouble();
      //      double minLongitude =
      //          range.values().stream()
      //              .mapToDouble(LatitudeLongitudePair::getLongitude)
      //              .min()
      //              .getAsDouble();
      //      double maxLongitude =
      //          range.values().stream()
      //              .mapToDouble(LatitudeLongitudePair::getLongitude)
      //              .max()
      //              .getAsDouble();
      //      System.out.println("Postcode range " + i + "XXXX:");
      //      System.out.println("\tmin lat=" + minLatitude);
      //      System.out.println("\tmax lat=" + maxLatitude);
      //      System.out.println("\tmin long=" + minLongitude);
      //      System.out.println("\tmax long=" + maxLongitude);
    }
  }
}
