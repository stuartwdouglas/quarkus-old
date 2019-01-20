package org.jboss.shamrock.jdbc.postresql.runtime.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.util.Properties;
import org.postgresql.Driver;

@TargetClass(Driver.class)
public final class DriverSubstitutions {

  @Substitute
  private void setupLoggerFromProperties(final Properties props) {
    // We don't want it to mess with the logger config
  }
}
