import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleZippedRecord implements Record {
  private final Map<String, String> map = new HashMap<>();

  public SimpleZippedRecord(List<String> attributes, List<String> values) {
    for (int i = 0; i < attributes.size(); i++) {
      map.put(attributes.get(i), values.get(i));
    }
    // TODO: hack!
    if (!values.get(6).equals("Employed")) {
      map.replace("commute_time", "N/A");
    }
  }

  @Override
  public Map<String, String> asMap() {
    return map;
  }
}
