import java.util.List;
import java.util.Set;

public interface GroupByStrategy {
  int getGroupIndex(String value);

  String getDescription();

  static GroupByStrategy groupBySet(List<Set<String>> sets) {
    return new GroupByStrategy() {
      @Override
      public int getGroupIndex(String value) {
        for (int i = 0; i < sets.size(); i++) {
          if (sets.get(i).contains(value)) {
            return i;
          }
        }
        throw new UnsupportedOperationException(
            "Could not find a group index for value: \"" + value + "\".");
      }

      @Override
      public String getDescription() {
        return sets.toString();
      }
    };
  }
}
