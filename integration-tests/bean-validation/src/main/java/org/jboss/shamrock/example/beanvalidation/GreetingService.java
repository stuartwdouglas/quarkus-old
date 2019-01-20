package org.jboss.shamrock.example.beanvalidation;

import javax.enterprise.context.ApplicationScoped;
import javax.validation.constraints.NotNull;

@ApplicationScoped
public class GreetingService {

  public String greeting(@NotNull String name) {
    return "hello " + name;
  }
}
