package com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.services;

import com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.commands.IngredientCommand;
import com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.converters.IngredientCommandToIngredient;
import com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.converters.IngredientToIngredientCommand;
import com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.domain.Ingredient;
import com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.domain.Recipe;
import com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.repositories.RecipeRepository;
import com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.repositories.reactive.RecipeReactiveRepository;
import com.eigenbaumarkt.spring5_mongodb_reactive_recipe_app.repositories.reactive.UnitOfMeasureReactiveRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Created by jt on 6/28/17.
 */
@Slf4j
@Service
public class IngredientServiceImpl implements IngredientService {

    private final IngredientToIngredientCommand ingredientToIngredientCommand;
    private final IngredientCommandToIngredient ingredientCommandToIngredient;
    private final RecipeReactiveRepository recipeReactiveRepository;
    private final RecipeRepository recipeRepository;
    private final UnitOfMeasureReactiveRepository unitOfMeasureReactiveRepository;

    public IngredientServiceImpl(IngredientToIngredientCommand ingredientToIngredientCommand,
                                 IngredientCommandToIngredient ingredientCommandToIngredient,
                                 RecipeReactiveRepository recipeReactiveRepository,
                                 RecipeRepository recipeRepository, UnitOfMeasureReactiveRepository unitOfMeasureReactiveRepository) {
        this.ingredientToIngredientCommand = ingredientToIngredientCommand;
        this.ingredientCommandToIngredient = ingredientCommandToIngredient;
        this.recipeReactiveRepository = recipeReactiveRepository;
        this.recipeRepository = recipeRepository;
        this.unitOfMeasureReactiveRepository = unitOfMeasureReactiveRepository;
    }

    @Override
    public Mono<IngredientCommand> findByRecipeIdAndIngredientId(String recipeId, String ingredientId) {

        return recipeReactiveRepository.findById(recipeId)
                .map(recipe -> recipe.getIngredients()
                .stream()
                .filter(ingredient -> ingredient.getId().equalsIgnoreCase(ingredientId))
                .findFirst())
                // continue if found:
                .filter(Optional::isPresent)
                // map is a transform operation:
                .map(ingredient -> {
                    IngredientCommand command = ingredientToIngredientCommand.convert(ingredient.get());
                    command.setRecipeId(recipeId);
                    return command;
                });

        /*
        if (!recipeOptional.isPresent()){
            //todo impl error handling
            log.error("recipe id not found. Id: " + recipeId);
        }

        Recipe recipe = recipeOptional.get();

        Optional<IngredientCommand> ingredientCommandOptional = recipe.getIngredients().stream()
                .filter(ingredient -> ingredient.getId().equals(ingredientId))
                .map( ingredient -> ingredientToIngredientCommand.convert(ingredient)).findFirst();

        if(!ingredientCommandOptional.isPresent()){
            //todo impl error handling
            log.error("Ingredient id not found: " + ingredientId);
        }

        //enhance command object with recipe id
        IngredientCommand ingredientCommand = ingredientCommandOptional.get();
        ingredientCommand.setRecipeId(recipe.getId());

        return Mono.just(ingredientCommandOptional.get());

         */
    }

    @Override
    public Mono<IngredientCommand> saveIngredientCommand(IngredientCommand command) {

        Optional<Recipe> recipeOptional = recipeRepository.findById(command.getRecipeId());

        if(!recipeOptional.isPresent()){

            //todo toss error if not found!
            log.error("Recipe not found for id: " + command.getRecipeId());
            return Mono.just(new IngredientCommand());
        } else {
            Recipe recipe = recipeOptional.get();

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(command.getId()))
                    .findFirst();

            if(ingredientOptional.isPresent()){
                Ingredient ingredientFound = ingredientOptional.get();
                ingredientFound.setDescription(command.getDescription());
                ingredientFound.setAmount(command.getAmount());
                ingredientFound.setUom(unitOfMeasureReactiveRepository
                        .findById(command.getUom().getId()).block());
                        // .orElseThrow(() -> new RuntimeException("UOM NOT FOUND"))); // todo address this
                if (ingredientFound.getUom() == null) {
                    new RuntimeException("No Unit of Measure objects found!");
                }
            } else {
                // add new Ingredient
                Ingredient ingredient = ingredientCommandToIngredient.convert(command);
                recipe.addIngredient(ingredient);
            }

            Recipe savedRecipe = recipeReactiveRepository.save(recipe).block();

            Optional<Ingredient> savedIngredientOptional = savedRecipe.getIngredients().stream()
                    .filter(recipeIngredients -> recipeIngredients.getId().equals(command.getId()))
                    .findFirst();

            // check by description
            if(!savedIngredientOptional.isPresent()){
                // not totally safe... But best guess
                savedIngredientOptional = savedRecipe.getIngredients().stream()
                        .filter(recipeIngredients -> recipeIngredients.getDescription().equals(command.getDescription()))
                        .filter(recipeIngredients -> recipeIngredients.getAmount().equals(command.getAmount()))
                        .filter(recipeIngredients -> recipeIngredients.getUom().getId().equals(command.getUom().getId()))
                        .findFirst();
            }

            //todo check for fail

            // enhance with id value
            IngredientCommand ingredientCommandSaved = ingredientToIngredientCommand.convert(savedIngredientOptional.get());
            ingredientCommandSaved.setRecipeId(recipe.getId());

            return Mono.just(ingredientCommandSaved);
        }

    }

    @Override
    public Mono<Void> deleteById(String recipeId, String idToDelete) {

        log.debug("Deleting ingredient: " + recipeId + ":" + idToDelete);

        // the reactive driver for mongodb does not support @DBRefs:
        // we get a list of recipe-Objects with "uom"-objects as properties,
        // but the "id" and "description" of the uoms are null
        // IMPORTANT TODO: DBRefs are not recommended, better store ids and load them at application level !!
        Recipe recipeReactive = recipeReactiveRepository.findById(recipeId).block(); // doesn't do anything, just left for TODO

        // non-reactive alternative:
        Recipe recipe = recipeRepository.findById(recipeId).get();

        if(recipe != null){

            log.debug("found recipe");

            Optional<Ingredient> ingredientOptional = recipe
                    .getIngredients()
                    .stream()
                    .filter(ingredient -> ingredient.getId().equals(idToDelete))
                    .findFirst();

            if(ingredientOptional.isPresent()){
                log.debug("found Ingredient");
                recipe.getIngredients().remove(ingredientOptional.get());
                recipeReactiveRepository.save(recipe).block();
            }
        } else {
            log.debug("Recipe Id not found. Id: " + recipeId);
        }

        return Mono.empty();
    }
}
