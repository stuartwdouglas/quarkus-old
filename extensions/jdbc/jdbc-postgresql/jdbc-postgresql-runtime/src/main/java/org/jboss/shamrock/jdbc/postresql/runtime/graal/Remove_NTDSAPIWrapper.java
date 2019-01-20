package org.jboss.shamrock.jdbc.postresql.runtime.graal;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;
import org.postgresql.sspi.NTDSAPIWrapper;

@TargetClass(NTDSAPIWrapper.class)
@Delete
public final class Remove_NTDSAPIWrapper {}
