package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.PageableRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.Period
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class OracleIntervalSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    IntervalRepository intervalRepository = context.getBean(IntervalRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    def cleanup() {
        intervalRepository.deleteAll()
    }

    void "test save, find and update single entity"() {
        given:
        def entity = new IntervalEntity()
        entity.setDuration(Duration.ofHours(4).negated())
        entity.setPeriod(Period.ofMonths(7))

        when:
        def savedEntity = intervalRepository.save(entity)

        then:
        savedEntity.id > 0

        when:
        def foundEntityOpt = intervalRepository.findById(savedEntity.id)
        def foundEntity = foundEntityOpt.orElse(null)

        then:
        foundEntity != null
        foundEntity.duration == entity.duration
        foundEntity.period == entity.period

        when:
        foundEntity.setDuration(Duration.ofHours(8))
        foundEntity.setPeriod(Period.ofMonths(10))
        intervalRepository.update(foundEntity)
        def updatedEntityOpt = intervalRepository.findById(savedEntity.id)
        def updatedEntity = updatedEntityOpt.orElse(null)

        then:
        updatedEntity != null
        updatedEntity.duration == foundEntity.duration
        updatedEntity.period == foundEntity.period
    }

    void "test save, find and update multiple entities"() {
        given:
        def entity1 = new IntervalEntity()
        entity1.setDuration(Duration.ofHours(4))
        entity1.setPeriod(Period.ofMonths(7))

        def entity2 = new IntervalEntity()
        entity2.setDuration(Duration.ofMinutes(5))
        entity2.setPeriod(Period.ofYears(8))

        when:
        def savedEntities = intervalRepository.saveAll([entity1, entity2])

        then:
        savedEntities != null
        savedEntities.size() == 2
        savedEntities.get(0).id != null
        savedEntities.get(1).id != null

        when:
        def foundEntities = intervalRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        foundEntities != null
        foundEntities.size() == 2
        foundEntities.get(0).duration == entity1.duration
        foundEntities.get(0).period == entity1.period
        foundEntities.get(1).duration == entity2.duration
        foundEntities.get(1).period == entity2.period

        when:
        entity1.setDuration(Duration.ofSeconds(30))
        entity1.setPeriod(Period.ofYears(5))
        entity2.setDuration(Duration.ofHours(5).plusMinutes(10).plusSeconds(14).plusMillis(250))
        entity2.setPeriod(Period.ofYears(2).plusMonths(4))
        intervalRepository.updateAll([entity1, entity2])
        def updatedEntities = intervalRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        updatedEntities != null
        updatedEntities.size() == 2
        updatedEntities.get(0).duration == entity1.duration
        updatedEntities.get(0).period == entity1.period
        updatedEntities.get(1).duration == entity2.duration
        updatedEntities.get(1).period == entity2.period
    }

    void "test save, find and update using custom queries"() {
        given:
        def duration1 = Duration.ofHours(4)
        def period1 = Period.ofMonths(7)
        def duration2 = Duration.ofHours(5)
        def period2 = Period.ofMonths(8)
        def duration3 = Duration.ofHours(6)
        def period3 = Period.ofMonths(9)
        def duration4 = Duration.ofHours(6)
        def period4 = Period.ofMonths(11)

        when:
        intervalRepository.saveCustom(duration1, period1)
        intervalRepository.saveCustom(duration2, period2)
        intervalRepository.saveCustom(duration3, period3)
        intervalRepository.saveCustom(duration4, period4)
        def savedEntities = intervalRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        savedEntities != null
        savedEntities.size() == 4
        savedEntities.get(0).duration == duration1
        savedEntities.get(0).period == period1
        savedEntities.get(1).duration == duration2
        savedEntities.get(1).period == period2
        savedEntities.get(2).duration == duration3
        savedEntities.get(2).period == period3
        savedEntities.get(3).duration == duration4
        savedEntities.get(3).period == period4

        when:
        def foundEntities = intervalRepository.findCustom(Duration.ofHours(4).plusMinutes(30), Period.ofMonths(10))

        then:
        foundEntities != null
        foundEntities.size() == 2
        foundEntities.get(0).duration == duration2
        foundEntities.get(0).period == period2
        foundEntities.get(1).duration == duration3
        foundEntities.get(1).period == period3

        when:
        intervalRepository.updateCustom(foundEntities.get(1).id, duration3.minusHours(3), period3)
        foundEntities = intervalRepository.findCustom(Duration.ofHours(4).plusMinutes(30), Period.ofMonths(10))

        then:
        foundEntities != null
        foundEntities.size() == 1
        foundEntities.get(0).duration == duration2
        foundEntities.get(0).period == period2
    }

    void "test save, find and update using async queries"() {
        given:
        def duration = Duration.ofHours(4)
        def period = Period.ofMonths(7)

        when:
        Future<Integer> saveResult = intervalRepository.saveAsync(duration, period)

        then:
        saveResult.get(3, TimeUnit.SECONDS) == 1

        when:
        Future<List<IntervalEntity>> findResult = intervalRepository.findAsync(duration, period)
        def entity = findResult.get(3, TimeUnit.SECONDS).get(0)

        then:
        entity.duration == duration
        entity.period == period

        when:
        Future<Integer> updateResult = intervalRepository.updateAsync(entity.id, Duration.ofHours(5), Period.ofMonths(8))

        then:
        updateResult.get(3, TimeUnit.SECONDS) != null
        with(intervalRepository.findById(entity.id)) {
            it.isPresent()
            it.get().duration == Duration.ofHours(5)
            it.get().period == Period.ofMonths(8)
        }
    }
}

@MappedEntity
class IntervalEntity {
    @Id
    @GeneratedValue
    Integer id
    Duration duration
    Period period
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface IntervalRepository extends PageableRepository<IntervalEntity, Integer> {

    @Query("INSERT INTO interval_entity(duration, period, id) VALUES (:dur, :per, INTERVAL_ENTITY_SEQ.nextval)")
    void saveCustom(@Parameter("dur") Duration duration, @Parameter("per") Period period)

    @Query("SELECT * FROM interval_entity WHERE duration > :dur AND period < :per ORDER BY id ASC")
    List<IntervalEntity> findCustom(@Parameter("dur") Duration duration, @Parameter("per") Period period)

    @Query("UPDATE interval_entity SET duration = :dur, period = :per WHERE id = :id")
    void updateCustom(Integer id, @Parameter("dur") Duration duration, @Parameter("per") Period period)

    @Query("INSERT INTO interval_entity(duration, period, id) VALUES (:dur, :per, INTERVAL_ENTITY_SEQ.nextval)")
    Future<Integer> saveAsync(@Parameter("dur") Duration duration, @Parameter("per") Period period)

    @Query("SELECT * FROM interval_entity WHERE duration = :dur AND period = :per")
    Future<List<IntervalEntity>> findAsync(@Parameter("dur") Duration duration, @Parameter("per") Period period)

    @Query("UPDATE interval_entity SET duration = :dur, period = :per WHERE id = :id")
    Future<Integer> updateAsync(Integer id, @Parameter("dur") Duration duration, @Parameter("per") Period period)
}
