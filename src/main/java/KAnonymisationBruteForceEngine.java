import static java.time.temporal.ChronoUnit.SECONDS;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class KAnonymisationBruteForceEngine {

  private static <T> T exit(int status) {
    System.exit(status);
    return null;
  }

  private static DataGrouper buildDataGrouperFromCsvFile(File csvFile) {
    CsvReader csvReader;
    try {
      csvReader = new CsvReader(csvFile);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return exit(1);
    }

    List<String> attributes;
    try {
      attributes = csvReader.readline();
    } catch (IOException e) {
      e.printStackTrace();
      return exit(1);
    }

    DataGrouper.Builder<SimpleZippedRecord> dataGrouperBuilder = DataGrouper.builder(attributes);
    try {
      List<String> datasetCsvRecordAsList;
      do {
        datasetCsvRecordAsList = csvReader.readline();
        if (!datasetCsvRecordAsList.isEmpty()) {
          dataGrouperBuilder.addRecord(new SimpleZippedRecord(attributes, datasetCsvRecordAsList));
        } else {
          break;
        }
      } while (true);
      csvReader.close();
    } catch (IOException e) {
      e.printStackTrace();
      return exit(1);
    }
    return dataGrouperBuilder.build();
  }

  public static class SimpleZippedRecord implements Record {
    private final Map<String, String> map = new HashMap<>();

    public SimpleZippedRecord(List<String> attributes, List<String> values) {
      for (int i = 0; i < attributes.size(); i++) {
        map.put(attributes.get(i), values.get(i));
      }
    }

    @Override
    public Map<String, String> asMap() {
      return map;
    }
  }

  private static class BruteForceResult implements Comparable<BruteForceResult> {
    private final double error;
    private final String groupByStrategyDescription;

    private BruteForceResult(double error, String groupByStrategyDescription) {
      this.error = error;
      this.groupByStrategyDescription = groupByStrategyDescription;
    }

    @Override
    public int compareTo(BruteForceResult that) {
      return Double.compare(this.error, that.error);
    }

    @Override
    public String toString() {
      return "<error=" + error + ", " + groupByStrategyDescription + ">";
    }
  }

  private static class ProgressTracker {
    private final int n;
    private final Semaphore semaphore = new Semaphore(0);
    private static final int PROGRESS_INCREMENTS = 1000;
    private int numberOfJobsCompleted = 0;
    private int progressCounter = 0;
    private LocalTime timeLastJobCompleted = LocalTime.now();

    private ProgressTracker(int n) {
      this.n = n;
    }

    public void markJobAsComplete() {
      semaphore.release();
      synchronized (this) {
        double progress = PROGRESS_INCREMENTS * (numberOfJobsCompleted++ / (double) n);
        if (progress > progressCounter) {
          LocalTime now = LocalTime.now();
          double percentageComplete = 100.0 * progressCounter / (double) PROGRESS_INCREMENTS;
          double secondsLeft =
              SECONDS.between(timeLastJobCompleted, now) * (PROGRESS_INCREMENTS - progress);
          System.out.print(
              "\r"
                  + percentageComplete
                  + "% complete; "
                  + (secondsLeft / 60.0)
                  + " minutes remaining");
          progressCounter++;
          timeLastJobCompleted = now;
        }
      }
    }

    public void join() throws InterruptedException {
      semaphore.acquire(n);
    }
  }

  public static void main(String[] args) {
    String csvFilePath = args[0];
    DataGrouper dataGrouper = buildDataGrouperFromCsvFile(new File(csvFilePath));

    List<GroupByStrategy> dobGroupByStrategies =
        Stream.of(5, 10, 20)
            .map(
                bucketSize ->
                    new GroupByStrategy() {
                      @Override
                      public int getGroupIndex(String dob) {
                        int yob = Integer.parseInt(dob.substring(4, 6));
                        int age = (121 - yob) % 100;
                        return age / bucketSize;
                      }

                      @Override
                      public String getDescription() {
                        return "Age ranges with bucket size = " + bucketSize;
                      }
                    })
            .collect(Collectors.toList());

    List<GroupByStrategy> postcodeGroupByRangeStrategies =
        Combinatorics.generateOrderedPowerSet(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"))
            .stream()
            .filter(
                rangeSpecification ->
                    rangeSpecification.stream().allMatch(range -> range.size() >= 3))
            .map(
                rangeSpecification ->
                    new GroupByStrategy() {
                      @Override
                      public int getGroupIndex(String value) {
                        if (value.equals("N/A")) {
                          return -1;
                        }
                        String firstDigit = "" + value.charAt(0);
                        for (int i = 0; i < rangeSpecification.size(); i++) {
                          if (rangeSpecification.get(i).contains(firstDigit)) {
                            return i;
                          }
                        }
                        throw new UnsupportedOperationException();
                      }

                      @Override
                      public String getDescription() {
                        return "Ranges based on the following first digit sets: "
                            + rangeSpecification;
                      }
                    })
            .collect(Collectors.toList());

    Function<Integer, GroupByStrategy> postcodeGroupByDigitsObfuscatedStrategyFactory =
        digitsObfuscated ->
            new GroupByStrategy() {
              @Override
              public int getGroupIndex(String value) {
                if (value.equals("N/A")) {
                  return -1;
                }
                return Integer.parseInt(value.substring(0, value.length() - digitsObfuscated));
              }

              @Override
              public String getDescription() {
                return "Postcode with " + digitsObfuscated + " digits obfuscated";
              }
            };

    List<GroupByStrategy> homePostcodeGroupByStrategies =
        IntStream.range(3, 5)
            .boxed()
            .map(postcodeGroupByDigitsObfuscatedStrategyFactory)
            .collect(Collectors.toList());
    homePostcodeGroupByStrategies.addAll(postcodeGroupByRangeStrategies);

    List<GroupByStrategy> workPostcodeGroupByStrategies =
        IntStream.range(3, 5)
            .boxed()
            .map(postcodeGroupByDigitsObfuscatedStrategyFactory)
            .collect(Collectors.toList());
    workPostcodeGroupByStrategies.addAll(postcodeGroupByRangeStrategies);

    List<String> educationLegend =
        List.of("Less than High School", "High School", "Bachelor", "Masters", "PhD/md");
    List<List<Set<String>>> educationGroupingOrderedPowerSet =
        Combinatorics.generateOrderedPowerSet(educationLegend);

    List<String> employmentLegend = List.of("Unemployed", "Student", "Employed", "Retired");
    List<List<Set<String>>> employmentGroupingOrderedPowerSet =
        Combinatorics.generateOrderedPowerSet(employmentLegend);

    List<String> accommodationLegend =
        List.of("Rent room", "Rent flat", "Rent house", "Own flat", "Own house");
    List<List<Set<String>>> accommodationGroupingOrderedPowerSet =
        Combinatorics.generateOrderedPowerSet(accommodationLegend);

    List<List<Set<String>>> childrenGroupings =
        List.of(
            List.of(
                Set.of("0"), Set.of("1"), Set.of("2"), Set.of("3", "4", "5", "6", "7", "8", "9")),
            List.of(Set.of("0"), Set.of("1"), Set.of("2", "3", "4", "5", "6", "7", "8", "9")),
            List.of(Set.of("0", "1"), Set.of("2", "3", "4", "5", "6", "7", "8", "9")),
            List.of(Set.of("0", "1"), Set.of("2", "3"), Set.of("4", "5", "6", "7", "8", "9")));

    List<String> numberVehiclesLegend = List.of("0", "1", "2", "3");
    List<List<Set<String>>> numberVehiclesGroupingOrderedPowerSet =
        Combinatorics.generateOrderedPowerSet(numberVehiclesLegend);

    int sizeOfBruteForce =
        dobGroupByStrategies.size()
            * homePostcodeGroupByStrategies.size()
            * workPostcodeGroupByStrategies.size()
            * educationGroupingOrderedPowerSet.size()
            * employmentGroupingOrderedPowerSet.size()
            * accommodationGroupingOrderedPowerSet.size()
            * childrenGroupings.size()
            * numberVehiclesGroupingOrderedPowerSet.size();
    System.out.println(sizeOfBruteForce + " brute force iterations to cover.");
    ProgressTracker progressTracker = new ProgressTracker(sizeOfBruteForce);

    double target = Math.sqrt(dataGrouper.getNumberOfRecords());
    int numberOfWorkers = Runtime.getRuntime().availableProcessors() - 1;
    int numberOfResults = 50;
    TruncatingConcurrentPriorityQueue<BruteForceResult> bruteForceResults =
        new TruncatingConcurrentPriorityQueue<>(numberOfResults);
    BlockingThreadPoolExecutor<Map<String, GroupByStrategy>> blockingThreadPoolExecutor =
        BlockingThreadPoolExecutor.start(
            numberOfWorkers,
            attributeToGroupByStrategyMap -> {
              List<Integer> sizes = dataGrouper.computeGroupSizes(attributeToGroupByStrategyMap);
              double error =
                  sizes.stream()
                      .mapToDouble(Integer::doubleValue)
                      .map(size -> target - size)
                      .map(diff -> diff * diff)
                      .average()
                      .getAsDouble();
              String description =
                  attributeToGroupByStrategyMap.values().stream()
                      .map(GroupByStrategy::getDescription)
                      .collect(Collectors.joining(" | "));
              bruteForceResults.offer(new BruteForceResult(error, description));
              progressTracker.markJobAsComplete();
            });

    for (GroupByStrategy dobGroupByStrategy : dobGroupByStrategies) {
      for (GroupByStrategy homePostcodeGroupByStrategy : homePostcodeGroupByStrategies) {
        for (GroupByStrategy workPostcodeGroupByStrategy : workPostcodeGroupByStrategies) {
          for (List<Set<String>> educationGroupingSets : educationGroupingOrderedPowerSet) {
            GroupByStrategy educationGroupByStrategy =
                GroupByStrategy.groupBySet(educationGroupingSets);
            for (List<Set<String>> employmentGroupingSets : employmentGroupingOrderedPowerSet) {
              GroupByStrategy employmentGroupByStrategy =
                  GroupByStrategy.groupBySet(employmentGroupingSets);
              for (List<Set<String>> accommodationGroupingSets :
                  accommodationGroupingOrderedPowerSet) {
                GroupByStrategy accommodationGroupByStrategy =
                    GroupByStrategy.groupBySet(accommodationGroupingSets);
                for (List<Set<String>> childrenGroupingSets : childrenGroupings) {
                  GroupByStrategy childrenGroupByStrategy =
                      GroupByStrategy.groupBySet(childrenGroupingSets);
                  for (List<Set<String>> numberVehiclesGroupingSets :
                      numberVehiclesGroupingOrderedPowerSet) {
                    GroupByStrategy numberVehiclesGroupByStrategy =
                        GroupByStrategy.groupBySet(numberVehiclesGroupingSets);
                    try {
                      blockingThreadPoolExecutor.submit(
                          Map.of(
                              "dob",
                              dobGroupByStrategy,
                              "home_postcode",
                              homePostcodeGroupByStrategy,
                              "work_postcode",
                              workPostcodeGroupByStrategy,
                              "education",
                              educationGroupByStrategy,
                              "employment",
                              employmentGroupByStrategy,
                              "accommodation",
                              accommodationGroupByStrategy,
                              "children",
                              childrenGroupByStrategy,
                              "number_vehicles",
                              numberVehiclesGroupByStrategy));
                    } catch (InterruptedException e) {
                      e.printStackTrace();
                      exit(1);
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    try {
      progressTracker.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
      exit(1);
    }
    blockingThreadPoolExecutor.forceShutdown();
    System.out.println();

    for (int i = 0; i < numberOfResults; i++) {
      System.out.println(bruteForceResults.poll());
    }
  }
}
