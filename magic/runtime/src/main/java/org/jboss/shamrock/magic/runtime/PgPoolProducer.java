package org.jboss.shamrock.magic.runtime;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import io.reactiverse.pgclient.PgPoolOptions;
import io.reactiverse.reactivex.pgclient.PgClient;
import io.reactiverse.reactivex.pgclient.PgPool;

@ApplicationScoped
public class PgPoolProducer {
	
	@Produces
	public PgPool getClient() {
	    // FIXME: config
		PgPoolOptions options = new PgPoolOptions()
				  .setPort(5432)
				  .setHost("localhost")
				  .setDatabase("rest-crud")
				  .setUser("restcrud")
				  .setPassword("restcrud")
				  .setMaxSize(50);

		// Create the client pool
		return PgClient.pool(options);
	}
}
