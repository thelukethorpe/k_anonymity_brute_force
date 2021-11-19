import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Assert;
import org.junit.Test;

public class TruncatingConcurrentPriorityQueueTest {

  @Test
  public void monkeyTest() {
    int numberOfThreads = 100;
    int queueCapacity = 10;
    TruncatingConcurrentPriorityQueue<Integer> queue =
        new TruncatingConcurrentPriorityQueue<>(queueCapacity);

    Collection<Thread> monkeys =
        IntStream.range(0, numberOfThreads)
            .boxed()
            .map(
                i ->
                    new Thread(
                        new Runnable() {
                          @Override
                          public void run() {
                            for (int j = 0; j < numberOfThreads; j++) {
                              queue.offer(i + j * numberOfThreads);
                            }
                          }
                        }))
            .collect(Collectors.toList());

    monkeys.forEach(Thread::start);
    for (Thread monkey : monkeys) {
      try {
        monkey.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    for (Integer i = 0; i < queueCapacity; i++) {
      Assert.assertEquals(queue.poll(), i);
    }
    Assert.assertTrue(queue.size() <= queueCapacity);
  }
}
