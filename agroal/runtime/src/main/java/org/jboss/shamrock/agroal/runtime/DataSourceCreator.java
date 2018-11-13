package org.jboss.shamrock.agroal.runtime;

import java.util.Map;

import javax.enterprise.context.spi.CreationalContext;

import org.jboss.protean.arc.BeanCreator;

public class DataSourceCreator implements BeanCreator<DataSourceDetails> {
    @Override
    public DataSourceDetails create(CreationalContext<DataSourceDetails> creationalContext, Map<String, Object> params) {
        try {
            DataSourceDetails producer = new DataSourceDetails();
            producer.setDriver(Class.forName((String) params.get("driver")));
            producer.setUrl((String) params.get("url"));
            producer.setUserName((String) params.get("username"));
            producer.setPassword((String) params.get("password"));
            producer.setMinSize((Integer) params.get("minsize"));
            producer.setMaxSize((Integer) params.get("maxsize"));
            return producer;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
