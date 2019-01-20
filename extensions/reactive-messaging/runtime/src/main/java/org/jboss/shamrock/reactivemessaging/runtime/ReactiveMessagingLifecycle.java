package org.jboss.shamrock.reactivemessaging.runtime;

import io.smallrye.reactive.messaging.extension.MediatorManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import org.jboss.shamrock.runtime.StartupEvent;

@Dependent
public class ReactiveMessagingLifecycle {

  @Inject MediatorManager mediatorManager;

  void onApplicationStart(@Observes StartupEvent event) {
    CompletableFuture<Void> future = mediatorManager.initializeAndRun();
    try {
      future.get();
    } catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
