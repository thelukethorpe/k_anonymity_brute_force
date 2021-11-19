import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DataGrouper {

  private final Map<String, String[]> attributeToColumnMap;
  private final int numberOfRecords;

  private DataGrouper(Map<String, String[]> attributeToColumnMap, int numberOfRecords) {
    this.attributeToColumnMap = attributeToColumnMap;
    this.numberOfRecords = numberOfRecords;
  }

  public int getNumberOfRecords() {
    return numberOfRecords;
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

    private Groups(List<Set<Integer>> groups) {
      this.groups = groups;
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

  public static <TRecord extends Record> Builder<TRecord> builder(List<String> attributes) {
    return new Builder<>(attributes);
  }

  public static class Builder<TRecord extends Record> {

    private final Map<String, List<String>> attributeToColumnMap;
    private int numberOfRecords = 0;

    public Builder(List<String> attributes) {
      this.attributeToColumnMap = new HashMap<>();
      attributes.forEach(attribute -> attributeToColumnMap.put(attribute, new LinkedList<>()));
    }

    public void addRecord(TRecord record) {
      for (Entry<String, String> attributeValuePair : record.asMap().entrySet()) {
        attributeToColumnMap.get(attributeValuePair.getKey()).add(attributeValuePair.getValue());
      }
      numberOfRecords++;
    }

    public DataGrouper build() {
      return new DataGrouper(
          attributeToColumnMap.entrySet().stream()
              .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().toArray(new String[] {}))),
          numberOfRecords);
    }
  }
}
