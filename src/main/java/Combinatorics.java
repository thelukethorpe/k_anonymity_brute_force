import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Combinatorics {

  public static <T> List<List<Set<T>>> generateOrderedPowerSet(List<T> values) {
    if (values.isEmpty()) {
      return Collections.singletonList(Collections.emptyList());
    }
    T head = values.get(0);
    List<List<Set<T>>> tailResult = generateOrderedPowerSet(values.subList(1, values.size()));
    List<List<Set<T>>> result =
        tailResult.stream()
            .map(
                list -> {
                  List<Set<T>> newList = new LinkedList<>(list);
                  newList.add(0, Set.of(head));
                  return newList;
                })
            .collect(Collectors.toList());
    tailResult.forEach(
        list -> {
          if (!list.isEmpty()) {
            Set<T> first = new HashSet<>(list.get(0));
            first.add(head);
            list.set(0, first);
            result.add(list);
          }
        });
    return result;
  }
}
