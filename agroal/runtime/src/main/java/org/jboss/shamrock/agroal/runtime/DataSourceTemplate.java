package org.jboss.shamrock.agroal.runtime;

import org.jboss.shamrock.annotations.runtime.Template;
import org.jboss.shamrock.runtime.BeanContainer;
import org.jboss.shamrock.runtime.ContextObject;

@Template
public class DataSourceTemplate {

    public void addDatasource(BeanContainer beanContainer,
                              String url, Class driver,
                              String userName, String password, Integer minSize, Integer maxSize) {
        DataSourceProducer producer = beanContainer.instance(DataSourceProducer.class);
        producer.setDriver(driver);
        producer.setUrl(url);
        producer.setUserName(userName);
        producer.setPassword(password);
        producer.setMinSize(minSize);
        producer.setMaxSize(maxSize);
    }

}
