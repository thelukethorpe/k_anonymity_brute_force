import static java.time.temporal.ChronoUnit.MILLIS;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class KAnonymisationBruteForceEngine {

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
          double millisecondsLeft =
              MILLIS.between(timeLastJobCompleted, now) * (PROGRESS_INCREMENTS - progress);
          System.out.print(
              "\r"
                  + percentageComplete
                  + "% complete; "
                  + Duration.of(Math.round(millisecondsLeft), MILLIS).toSeconds()
                  + " seconds remaining");
          progressCounter++;
          timeLastJobCompleted = now;
        }
      }
    }

    public void join() throws InterruptedException {
      semaphore.acquire(n);
    }
  }

  private static class OffsetBucketSizePair {
    private final int offset;
    private final int bucketSize;

    private OffsetBucketSizePair(int offset, int bucketSize) {
      this.offset = offset;
      this.bucketSize = bucketSize;
    }

    @Override
    public String toString() {
      return "<offset = " + offset + ", bucket size = " + bucketSize + ">";
    }
  }

  public static void main(String[] args) {
    String csvFilePath = args[0];
    DataFrame dataFrame;
    try {
      dataFrame = DataFrame.buildDataFrameFromCsvFile(new File(csvFilePath));
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
      return;
    }

    List<GroupByStrategy> dobGroupByStrategies =
        Stream.of(
                new OffsetBucketSizePair(0, 10),
                new OffsetBucketSizePair(0, 20),
                new OffsetBucketSizePair(10, 20),
                new OffsetBucketSizePair(19, 18),
                new OffsetBucketSizePair(19, 20),
                new OffsetBucketSizePair(19, 25))
            .map(
                offsetBucketSizePair ->
                    new GroupByStrategy() {
                      @Override
                      public int getGroupIndex(String dob) {
                        int yob = Integer.parseInt(dob.substring(4, 6));
                        int age = (121 - yob) % 100;
                        return (age - offsetBucketSizePair.offset)
                            / offsetBucketSizePair.bucketSize;
                      }

                      @Override
                      public String getDescription() {
                        return "Age ranges with " + offsetBucketSizePair.toString();
                      }
                    })
            .collect(Collectors.toList());

    //    List<GroupByStrategy> postcodeGroupByRangeStrategies =
    //        Combinatorics.generateOrderedPowerSet(List.of("1", "2", "3", "4", "5", "6", "7", "8",
    // "9"))
    //            .stream()
    //            .filter(
    //                rangeSpecification ->
    //                    rangeSpecification.stream().allMatch(range -> range.size() >= 3))
    //            .map(
    //                rangeSpecification ->
    //                    new GroupByStrategy() {
    //                      @Override
    //                      public int getGroupIndex(String value) {
    //                        if (value.equals("N/A")) {
    //                          return -1;
    //                        }
    //                        String firstDigit = "" + value.charAt(0);
    //                        for (int i = 0; i < rangeSpecification.size(); i++) {
    //                          if (rangeSpecification.get(i).contains(firstDigit)) {
    //                            return i;
    //                          }
    //                        }
    //                        throw new UnsupportedOperationException();
    //                      }
    //
    //                      @Override
    //                      public String getDescription() {
    //                        return "Ranges based on the following first digit sets: "
    //                            + rangeSpecification;
    //                      }
    //                    })
    //            .collect(Collectors.toList());

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

    List<GroupByStrategy> workPostcodeGroupByStrategies =
        IntStream.range(3, 5)
            .boxed()
            .map(postcodeGroupByDigitsObfuscatedStrategyFactory)
            .collect(Collectors.toList());
    //    workPostcodeGroupByStrategies.addAll(postcodeGroupByRangeStrategies);

    List<String> educationLegend =
        List.of("Less than High School", "High School", "Bachelor", "Masters", "PhD/md");
    List<List<Set<String>>> educationGroupingOrderedPowerSet =
        Combinatorics.generateOrderedPowerSet(educationLegend);
    educationGroupingOrderedPowerSet.removeIf(list -> list.size() == 1);

    List<List<Set<String>>> accommodationGroupingOrderedPowerSet =
        List.of(
            List.of(
                Set.of("Rent room"),
                Set.of("Rent flat", "Own flat"),
                Set.of("Own house", "Rent house")),
            List.of(
                Set.of("Rent room", "Rent flat", "Own flat"), Set.of("Own house", "Rent house")));

    List<List<Set<String>>> childrenGroupings =
        List.of(
            List.of(Set.of("0"), Set.of("1", "2", "3", "4", "5", "6", "7", "8", "9")),
            List.of(
                Set.of("0"), Set.of("1"), Set.of("2"), Set.of("3", "4", "5", "6", "7", "8", "9")),
            List.of(Set.of("0"), Set.of("1"), Set.of("2", "3", "4", "5", "6", "7", "8", "9")),
            List.of(Set.of("0", "1"), Set.of("2", "3", "4", "5", "6", "7", "8", "9")),
            List.of(Set.of("0", "1"), Set.of("2", "3"), Set.of("4", "5", "6", "7", "8", "9")));

    List<String> numberOfCoMorbiditiesLegend = List.of("0", "1", "2", "3");
    List<List<Set<String>>> numberOfCoMorbiditiesGroupingOrderedPowerSet =
        Combinatorics.generateOrderedPowerSet(numberOfCoMorbiditiesLegend);
    numberOfCoMorbiditiesGroupingOrderedPowerSet.removeIf(list -> list.size() == 1);

    int sizeOfBruteForce =
        dobGroupByStrategies.size()
            * workPostcodeGroupByStrategies.size()
            * educationGroupingOrderedPowerSet.size()
            * accommodationGroupingOrderedPowerSet.size()
            * childrenGroupings.size()
            * numberOfCoMorbiditiesGroupingOrderedPowerSet.size();
    System.out.println(sizeOfBruteForce + " brute force iterations to cover.");
    ProgressTracker progressTracker = new ProgressTracker(sizeOfBruteForce);

    double targetK = 3;
    int numberOfWorkers = Runtime.getRuntime().availableProcessors() - 1;
    int numberOfResults = 50;
    TruncatingConcurrentPriorityQueue<BruteForceResult> bruteForceResults =
        new TruncatingConcurrentPriorityQueue<>(numberOfResults);
    BlockingThreadPoolExecutor<Map<String, GroupByStrategy>> blockingThreadPoolExecutor =
        BlockingThreadPoolExecutor.start(
            numberOfWorkers,
            attributeToGroupByStrategyMap -> {
              List<Integer> sizes = dataFrame.computeGroupSizes(attributeToGroupByStrategyMap);
              int error = sizes.stream().filter(size -> size < targetK).mapToInt(i -> i).sum();
              String description =
                  attributeToGroupByStrategyMap.entrySet().stream()
                      .map(e -> e.getKey() + ": " + e.getValue().getDescription())
                      .collect(Collectors.joining(" | "));
              bruteForceResults.offer(new BruteForceResult(error, description));
              progressTracker.markJobAsComplete();
            });

    for (GroupByStrategy dobGroupByStrategy : dobGroupByStrategies) {
      for (GroupByStrategy workPostcodeGroupByStrategy : workPostcodeGroupByStrategies) {
        for (List<Set<String>> educationGroupingSets : educationGroupingOrderedPowerSet) {
          GroupByStrategy educationGroupByStrategy =
              GroupByStrategy.groupBySet(educationGroupingSets);
          for (List<Set<String>> accommodationGroupingSets : accommodationGroupingOrderedPowerSet) {
            GroupByStrategy accommodationGroupByStrategy =
                GroupByStrategy.groupBySet(accommodationGroupingSets);
            for (List<Set<String>> childrenGroupingSets : childrenGroupings) {
              GroupByStrategy childrenGroupByStrategy =
                  GroupByStrategy.groupBySet(childrenGroupingSets);
              for (List<Set<String>> numberOfCoMorbiditiesGroupingSets :
                  numberOfCoMorbiditiesGroupingOrderedPowerSet) {
                GroupByStrategy numberOfCoMorbiditiesGroupByStrategy =
                    GroupByStrategy.groupBySet(numberOfCoMorbiditiesGroupingSets);
                try {
                  blockingThreadPoolExecutor.submit(
                      Map.of(
                          "gender",
                          new GroupByStrategy() {
                            @Override
                            public int getGroupIndex(String value) {
                              return value.equals("male") ? 0 : 1;
                            }

                            @Override
                            public String getDescription() {
                              return "male or female";
                            }
                          },
                          "dob",
                          dobGroupByStrategy,
                          "number_of_co-morbidities",
                          numberOfCoMorbiditiesGroupByStrategy,
                          "children",
                          childrenGroupByStrategy,
                          "number_vehicles",
                          new GroupByStrategy() {
                            @Override
                            public int getGroupIndex(String value) {
                              return value.equals("0") ? 0 : 1;
                            }

                            @Override
                            public String getDescription() {
                              return "has a vehicle or doesn't";
                            }
                          },
                          "accommodation",
                          accommodationGroupByStrategy,
                          "work_postcode",
                          workPostcodeGroupByStrategy,
                          "commute_time",
                          new GroupByStrategy() {
                            @Override
                            public int getGroupIndex(String value) {
                              if (value.equals("N/A")) {
                                return -1;
                              } else if (Double.parseDouble(value) > 0) {
                                return 1;
                              }
                              return 0;
                            }

                            @Override
                            public String getDescription() {
                              return "unemployed, employed with no commute or commute";
                            }
                          },
                          "education",
                          educationGroupByStrategy));
                } catch (InterruptedException e) {
                  e.printStackTrace();
                  System.exit(1);
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
      System.exit(1);
    }
    blockingThreadPoolExecutor.forceShutdown();
    System.out.println();

    for (int i = 0; i < numberOfResults; i++) {
      System.out.println(i + ": " + bruteForceResults.poll().toString());
    }
  }
}
