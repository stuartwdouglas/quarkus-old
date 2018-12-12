package org.jboss.shamrock.magic.runtime;

import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import org.jboss.protean.arc.Arc;

import io.reactiverse.pgclient.Row;
import io.reactiverse.reactivex.pgclient.PgPool;
import io.reactiverse.reactivex.pgclient.Tuple;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;

@NotReallyJpa
@MappedSuperclass
public class RxModel {
    
    @Id
    public Integer id;
    
    private static PgPool getPgPool() {
        return Arc.container().instance(PgPool.class).get();
    }

    public Single<? extends RxModel> save() {
        PgPool pool = getPgPool();
        RxModelInfo<RxModel> modelInfo = (RxModelInfo)getModelInfo();
        if(id == null)
            return pool.rxPreparedQuery("SELECT nextval('hibernate_sequence') AS id")
                    .map(rowset -> rowset.iterator().next().getInteger("id"))
                    .flatMap(id -> pool.rxPreparedQuery(modelInfo.insertStatement(), modelInfo.toTuple(this))
                            .map(rowset -> {
                                this.id = id;
                                return this;
                            }));
        else
            return pool.rxPreparedQuery(modelInfo.updateStatement(), modelInfo.toTuple(this))
                    .map(rowset -> this);
    }

    public Completable delete() {
        PgPool pool = getPgPool();
        return pool.rxPreparedQuery("DELETE FROM "+getModelInfo().getEntityClass().getName()+" WHERE id = $1", Tuple.of(id)).ignoreElement();
    }

    protected RxModelInfo<? extends RxModel> getModelInfo(){
        throw new RuntimeException("Should never be called");
    }
    
    //
    // Static Helpers
    
    public static <T extends RxModel> Observable<T> findAll() {
        throw new RuntimeException("Should never be called");
    }

    protected static <T extends RxModel> Observable<T> findAll(RxModelInfo<T> modelInfo) {
        PgPool pool = getPgPool();
        // FIXME: order by from model info
        return pool.rxQuery("SELECT * FROM "+modelInfo.getEntityClass().getName()+" ORDER BY name")
                .flatMapObservable(rowset -> Observable.fromIterable(rowset.getDelegate()))
                .map(row -> modelInfo.fromRow(row));
    }

    public static <T extends RxModel> Single<T> findById(Integer id) {
        throw new RuntimeException("Should never be called");
    }
    
    protected static <T extends RxModel> Single<T> findById(RxModelInfo<T> modelInfo, Integer id) {
        PgPool pool = getPgPool();
        return pool.rxPreparedQuery("SELECT * FROM "+modelInfo.getEntityClass().getName()+" WHERE id = $1", Tuple.of(id))
                .flatMapObservable(rowset -> Observable.fromIterable(rowset.getDelegate()))
                .firstOrError()
                .map(row -> modelInfo.fromRow(row));
    }

    public interface RxModelInfo<T extends RxModel> {
        Class<T> getEntityClass();
        T fromRow(Row row);
        String insertStatement();
        String updateStatement();
        Tuple toTuple(T entity);
    }
}
