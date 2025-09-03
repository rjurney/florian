package com.yangdb.fuse.epb.plan.extenders;

/*-
 * #%L
 * fuse-dv-epb
 * %%
 * Copyright (C) 2016 - 2019 The YangDb Graph Database Project
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.yangdb.fuse.model.asgQuery.AsgQueryUtil;
import com.yangdb.fuse.dispatcher.utils.PlanUtil;
import com.yangdb.fuse.model.asgQuery.AsgEBase;
import com.yangdb.fuse.model.asgQuery.AsgQuery;
import com.yangdb.fuse.model.execution.plan.AsgEBaseContainer;
import com.yangdb.fuse.model.execution.plan.PlanOp;
import com.yangdb.fuse.model.execution.plan.composite.Plan;
import com.yangdb.fuse.model.execution.plan.entity.EntityJoinOp;
import com.yangdb.fuse.model.execution.plan.entity.EntityOp;
import com.yangdb.fuse.model.query.EBase;
import com.yangdb.fuse.model.query.Rel;
import com.yangdb.fuse.model.query.Start;
import com.yangdb.fuse.model.query.aggregation.CountComp;
import com.yangdb.fuse.model.query.entity.EConcrete;
import com.yangdb.fuse.model.query.entity.EEntityBase;
import com.yangdb.fuse.model.query.entity.ETyped;
import com.yangdb.fuse.model.query.entity.EUntyped;
import com.yangdb.fuse.model.query.optional.OptionalComp;
import com.yangdb.fuse.model.query.properties.EProp;
import com.yangdb.fuse.model.query.properties.EPropGroup;
import com.yangdb.fuse.model.query.properties.RelProp;
import com.yangdb.fuse.model.query.properties.RelPropGroup;
import javaslang.Tuple2;
import javaslang.collection.Stream;

import java.util.*;

/**
 * Created by moti on 2/28/2017.
 */
public interface SimpleExtenderUtils {
    static Map<Integer, AsgEBase> flattenQuery(AsgQuery query) {
        Map<Integer, AsgEBase> elements = new HashMap<>();
        flattenQueryRecursive(query.getStart(), elements);
        return elements;
    }

    static void flattenQueryRecursive(AsgEBase element, Map<Integer, AsgEBase> allElements) {
        if (!allElements.containsKey(element.geteNum())) {
            if (shouldAddElement(element)) {
                allElements.put(element.geteNum(), element);
            }

            if (element.getNext() != null) {
                element.getNext().forEach(e -> flattenQueryRecursive((AsgEBase) e, allElements));
            }

            if (shouldAdvanceToBs(element) && element.getB() != null) {
                element.getB().forEach(e -> flattenQueryRecursive((AsgEBase) e, allElements));
            }
        }
    }

    static boolean shouldAdvanceToBs(AsgEBase element) {
        return element.getB() != null;
    }

    static boolean shouldAddElement(AsgEBase element) {
        return element != null &&
                !(element.geteBase() instanceof Start) &&
                !(element.geteBase() instanceof CountComp) &&
                !(element.geteBase() instanceof OptionalComp);
    }

    /**
     * Takes a flattened query and an execution plan, and seperates the query getTo two collections - parts that exist
     * in the plan and parts that do not exist
     *
     * @param plan
     * @param queryParts
     * @return A tuple, where the first element is the query parts that exist in the plan ("handled"), and a map with the
     * "unhandled" parts
     */
    static <C> Tuple2<List<AsgEBase>, Map<Integer, AsgEBase>> removeHandledQueryParts(Plan plan, Map<Integer, AsgEBase> queryParts) {
        Map<Integer, AsgEBase> unHandledParts = new HashMap<>(queryParts);
        List<AsgEBase> handledParts = new LinkedList<>();

        Stream.ofAll(PlanUtil.flat(plan).getOps())
                .filter(op -> AsgEBaseContainer.class.isAssignableFrom(op.getClass()))
                .map(op -> (AsgEBaseContainer) op)
                .forEach(op -> {
                    handledParts.add(queryParts.get(op.getAsgEbase().geteNum()));
                    unHandledParts.remove(op.getAsgEbase().geteNum());
                });
        return new Tuple2<>(handledParts, unHandledParts);
    }

    static boolean shouldAdvanceToParents(AsgEBase<? extends EBase> handledPartToExtend) {
        return handledPartToExtend.getParents() != null;
    }

    static <C> boolean checkIfPlanIsComplete(Plan plan, AsgQuery query) {
        boolean allPartsCovered = checkIfAllPlanPartsCovered(plan, query);
        return allPartsCovered && checkIfAllJoinsAreComplete(plan);
    }

    static boolean checkIfAllJoinsAreComplete(Plan plan) {
        boolean allValid = true;
        for (PlanOp planOp : plan.getOps()) {
            if (planOp instanceof EntityJoinOp) {
                allValid &= checkJoinRecursive((EntityJoinOp) planOp);
            }
        }
        return allValid;
    }

    static boolean checkJoinRecursive(EntityJoinOp planOp) {
        return planOp.isComplete() && checkIfAllJoinsAreComplete(planOp.getLeftBranch()) && checkIfAllJoinsAreComplete(planOp.getRightBranch());
    }

    static boolean checkIfAllPlanPartsCovered(Plan plan, AsgQuery query) {
        Map<Integer, AsgEBase> queryParts = SimpleExtenderUtils.flattenQuery(query);
        Tuple2<List<AsgEBase>, Map<Integer, AsgEBase>> partsTuple = SimpleExtenderUtils.removeHandledQueryParts(plan, queryParts);

        Set<Class<?>> handledClasses = new HashSet<>(Arrays.asList(
                EConcrete.class, EUntyped.class, ETyped.class, Rel.class, EProp.class,
                EPropGroup.class, RelProp.class, RelPropGroup.class));

        return Stream.ofAll(partsTuple._2().values())
                .filter(asgEBase -> handledClasses.contains(asgEBase.geteBase().getClass()))
                .isEmpty();
    }


    static Set<Integer> markEntitiesAndRelations(Plan plan) {
        return Stream.ofAll(PlanUtil.flat(plan).getOps())
                .filter(op -> AsgEBaseContainer.class.isAssignableFrom(op.getClass()))
                .map(op -> (AsgEBaseContainer) op)
                .map(op -> op.getAsgEbase().geteNum()).toJavaSet();
    }


    static <T extends EBase, S extends EBase> Optional<AsgEBase<S>> getNextDescendantUnmarkedOfType(Class<? extends EBase> type,
                                                                                                    AsgEBase<T> asgEBase,
                                                                                                    Set<Integer> markedElements) {
        return AsgQueryUtil.nextDescendant(asgEBase,
                (child) -> type.isAssignableFrom(child.geteBase().getClass()) &&
                        !markedElements.contains(child.geteNum()));
    }

    static <T extends EBase, S extends EBase> Optional<AsgEBase<S>> getNextAncestorUnmarkedOfType(Class<? extends EBase> type,
                                                                                                  AsgEBase<T> asgEBase,
                                                                                                  Set<Integer> markedElements) {
        return AsgQueryUtil.ancestor(asgEBase,
                (child) -> type.isAssignableFrom(child.geteBase().getClass()) &&
                        !markedElements.contains(child.geteNum()));
    }

    /**
     * get next Rel type element which was not visited already
     *
     * @param plan
     * @return
     */
    static <T extends EBase> Optional<AsgEBase<T>> getNextDescendantUnmarkedOfType(Plan plan, Class<T> type) {
        Set<Integer> markedElements = markEntitiesAndRelations(plan);
        Optional<EntityOp> lastEntityOp = PlanUtil.last(plan, EntityOp.class);
        if (!lastEntityOp.isPresent()) {
            return Optional.empty();
        }

        Optional<AsgEBase<T>> nextRelation = getNextDescendantUnmarkedOfType(type, lastEntityOp.get().getAsgEbase(), markedElements);
        if (!nextRelation.isPresent()) {
            Optional<AsgEBase<EEntityBase>> parentEntity = AsgQueryUtil.ancestor(lastEntityOp.get().getAsgEbase(), EEntityBase.class);

            while (parentEntity.isPresent()) {
//              get first Rel element in path from source (current plan entity) to next ancestor entity
                nextRelation = AsgQueryUtil.findFirstInPath(lastEntityOp.get().getAsgEbase(), parentEntity.get()
                        , asgEBase -> Rel.class.isAssignableFrom(asgEBase.geteBase().getClass()) && !markedElements.contains(asgEBase.geteNum()));
                if (nextRelation.isPresent()) {
                    break;
                }
                //if not found in path - search descendant (not neccessary in path)
                nextRelation = getNextDescendantUnmarkedOfType(type, parentEntity.get(), markedElements);
                if (nextRelation.isPresent()) {
                    break;
                }
                parentEntity = AsgQueryUtil.ancestor(parentEntity.get(), EEntityBase.class);
            }
        }

        return nextRelation;
    }

    /**
     * get next Rel type element which was not visited already
     *
     * @param plan
     * @return
     */
    static <T extends EBase> List<AsgEBase<T>> getNextDescendantsUnmarkedOfType(Plan plan, Class<T> type) {

        Optional<EntityOp> lastEntityOp = PlanUtil.last(plan, EntityOp.class);
        if (!lastEntityOp.isPresent()) {
            return Collections.emptyList();
        }

        return AsgQueryUtil.nextDescendants(lastEntityOp.get().getAsgEbase(), (child) -> type.isAssignableFrom(child.geteBase().getClass()),
                p -> {
                    if (p.equals(lastEntityOp.get().getAsgEbase()))
                        return true;

                    List path = AsgQueryUtil.pathToAncestor(p, lastEntityOp.get().getAsgEbase().geteBase().geteNum());
                    return !path.isEmpty() && path.size() < 3;
                });
    }


    /**
     * get next Rel type element which was not visited already
     *
     * @param plan
     * @return
     */
    static <T extends EBase> Optional<AsgEBase<T>> getNextAncestorUnmarkedOfType(Plan plan, Class<T> type) {
        Set<Integer> markedElements = markEntitiesAndRelations(plan);
        Optional<EntityOp> lastEntityOp = PlanUtil.last(plan, EntityOp.class);
        return getNextAncestorUnmarkedOfType(type, lastEntityOp.get().getAsgEbase(), markedElements);
    }

    /**
     * get next Rel type element which was not visited already
     *
     * @param plan
     * @return
     */
    static <T extends EBase> Optional<AsgEBase<T>> getNextAncestorOfType(Plan plan, Class<T> type) {
        Optional<EntityOp> lastEntityOp = PlanUtil.last(plan, EntityOp.class);
        return getNextAncestorUnmarkedOfType(type, lastEntityOp.get().getAsgEbase(), Collections.EMPTY_SET);
    }
}

