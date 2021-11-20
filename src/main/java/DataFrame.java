import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DataFrame {

  private final Map<String, String[]> attributeToColumnMap;
  private final int numberOfRecords;

  private DataFrame(Map<String, String[]> attributeToColumnMap, int numberOfRecords) {
    this.attributeToColumnMap = attributeToColumnMap;
    this.numberOfRecords = numberOfRecords;
  }

  public int getNumberOfRecords() {
    return numberOfRecords;
  }

  public List<Record> getRecords() {
    List<Entry<String, String[]>> attributeColumnPairs =
        new ArrayList<>(attributeToColumnMap.entrySet());
    return IntStream.range(0, numberOfRecords)
        .boxed()
        .map(
            i ->
                new Record() {
                  @Override
                  public Map<String, String> asMap() {
                    return attributeColumnPairs.stream()
                        .collect(Collectors.toMap(Entry::getKey, e -> e.getValue()[i]));
                  }
                })
        .collect(Collectors.toList());
  }

  public List<Integer> computeGroupSizes(
      Map<String, GroupByStrategy> attributeToGroupByStrategyMap) {
    Groups result =
        new Groups(IntStream.range(0, numberOfRecords).boxed().collect(Collectors.toSet()));
    for (Entry<String, GroupByStrategy> attributeGroupByStrategyPair :
        attributeToGroupByStrategyMap.entrySet()) {
      String[] column = attributeToColumnMap.get(attributeGroupByStrategyPair.getKey());
      GroupByStrategy groupByStrategy = attributeGroupByStrategyPair.getValue();
      result.split(column, groupByStrategy);
    }
    return result.getGroupSizes();
  }

  private static class Groups {
    private final List<Set<Integer>> groups;

    public Groups(Set<Integer> initialDomain) {
      this.groups = new ArrayList<>(1);
      this.groups.add(initialDomain);
    }

    public List<Integer> getGroupSizes() {
      return groups.stream().map(Set::size).collect(Collectors.toList());
    }

    public void split(String[] column, GroupByStrategy groupByStrategy) {
      List<Set<Integer>> newGroups = new LinkedList<>();
      for (Set<Integer> group : groups) {
        Map<Integer, Set<Integer>> groupByIndexToGroupMap = new HashMap<>();
        for (int id : group) {
          String value = column[id];
          int groupByIndex = groupByStrategy.getGroupIndex(value);
          groupByIndexToGroupMap.putIfAbsent(groupByIndex, new HashSet<>());
          groupByIndexToGroupMap.get(groupByIndex).add(id);
        }
        newGroups.addAll(groupByIndexToGroupMap.values());
      }
      this.groups.clear();
      this.groups.addAll(newGroups);
    }
  }

  public static Builder builder(List<String> attributes) {
    return new Builder(attributes);
  }

  public static class Builder {

    private final Map<String, List<String>> attributeToColumnMap;
    private int numberOfRecords = 0;

    public Builder(List<String> attributes) {
      this.attributeToColumnMap = new HashMap<>();
      attributes.forEach(attribute -> attributeToColumnMap.put(attribute, new LinkedList<>()));
    }

    public void addRecord(Record record) {
      for (Entry<String, String> attributeValuePair : record.asMap().entrySet()) {
        attributeToColumnMap.get(attributeValuePair.getKey()).add(attributeValuePair.getValue());
      }
      numberOfRecords++;
    }

    public DataFrame build() {
      return new DataFrame(
          attributeToColumnMap.entrySet().stream()
              .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().toArray(new String[] {}))),
          numberOfRecords);
    }
  }

  public static DataFrame buildDataFrameFromCsvFile(File csvFile) throws IOException {
    CsvReader csvReader = new CsvReader(csvFile);
    List<String> attributes = csvReader.readline();
    DataFrame.Builder dataGrouperBuilder = DataFrame.builder(attributes);
    List<String> datasetCsvRecordAsList;
    do {
      datasetCsvRecordAsList = csvReader.readline();
      if (datasetCsvRecordAsList.isEmpty()) {
        break;
      }
      dataGrouperBuilder.addRecord(new SimpleZippedRecord(attributes, datasetCsvRecordAsList));
    } while (true);
    csvReader.close();
    return dataGrouperBuilder.build();
  }
}
