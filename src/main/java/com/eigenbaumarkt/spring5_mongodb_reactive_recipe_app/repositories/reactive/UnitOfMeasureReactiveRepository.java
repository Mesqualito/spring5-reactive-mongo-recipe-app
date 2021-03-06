package com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.repositories.reactive;

import com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.domain.UnitOfMeasure;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface UnitOfMeasureReactiveRepository extends ReactiveMongoRepository<UnitOfMeasure, String> {

    Mono<UnitOfMeasure> findByDescription(String description);

}
