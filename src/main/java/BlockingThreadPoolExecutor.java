import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BlockingThreadPoolExecutor<TWork> {
  private final LinkedBlockingQueue<TWork> workQueue;
  private final List<Thread> workers;

  private BlockingThreadPoolExecutor(int numberOfWorkers, Consumer<TWork> doWork) {
    this.workQueue = new LinkedBlockingQueue<>(numberOfWorkers);
    this.workers =
        IntStream.range(0, numberOfWorkers)
            .boxed()
            .map(i -> this.toString() + "_Worker#" + i)
            .map(
                workerName ->
                    new Thread(
                        new Runnable() {
                          @Override
                          public void run() {
                            Exception workerFailureException = null;
                            do {
                              try {
                                doWork.accept(workQueue.take());
                              } catch (Exception exception) {
                                workerFailureException = exception;
                              }
                            } while (workerFailureException == null);
                            workerFailureException.printStackTrace();
                          }
                        },
                        workerName))
            .collect(Collectors.toList());
  }

  public static <TWork> BlockingThreadPoolExecutor<TWork> start(
      int numberOfWorkers, Consumer<TWork> doWork) {
    BlockingThreadPoolExecutor<TWork> blockingThreadPoolExecutor =
        new BlockingThreadPoolExecutor<>(numberOfWorkers, doWork);
    blockingThreadPoolExecutor.start();
    return blockingThreadPoolExecutor;
  }

  private void start() {
    workers.forEach(Thread::start);
  }

  public void submit(TWork work) throws InterruptedException {
    workQueue.put(work);
  }

  public void forceShutdown() {
    workers.forEach(Thread::interrupt);
  }
}
