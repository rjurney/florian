package com.yangdb.fuse.asg.strategy.validation;

import com.yangdb.fuse.asg.validation.AsgStartEntityValidatorStrategy;
import com.yangdb.fuse.model.asgQuery.AsgQueryUtil;
import com.yangdb.fuse.model.OntologyTestUtils;
import com.yangdb.fuse.model.asgQuery.AsgEBase;
import com.yangdb.fuse.model.asgQuery.AsgQuery;
import com.yangdb.fuse.model.asgQuery.AsgStrategyContext;
import com.yangdb.fuse.model.ontology.Ontology;
import com.yangdb.fuse.model.query.Start;
import com.yangdb.fuse.model.query.properties.RelProp;
import com.yangdb.fuse.model.validation.ValidationResult;
import javaslang.collection.Stream;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static com.yangdb.fuse.model.OntologyTestUtils.START_DATE;
import static com.yangdb.fuse.model.asgQuery.AsgQuery.Builder.*;
import static com.yangdb.fuse.model.query.Rel.Direction.R;
import static com.yangdb.fuse.model.query.properties.constraint.Constraint.of;
import static com.yangdb.fuse.model.query.properties.constraint.ConstraintOp.eq;

/**
 * Created by lior.perry on 6/6/2017.
 */
public class AsgStartEntityValidatorStrategyTest {
    Ontology ontology;

    AsgQuery query = AsgQuery.Builder.start("Q1", "Dragons")
            .next(unTyped(1))
            .next(rel(2, OntologyTestUtils.OWN.getrType(), R).below(relProp(10,
                    RelProp.of(10, START_DATE.type, of(eq, new Date())))))
            .next(unTyped(3))
            .build();

    @Before
    public void setUp() throws Exception {
        ontology = OntologyTestUtils.createDragonsOntologyLong();
    }

    @Test
    public void testValidQuery() {
        AsgStartEntityValidatorStrategy strategy = new AsgStartEntityValidatorStrategy();
        ValidationResult validationResult = strategy.apply(query, new AsgStrategyContext(new Ontology.Accessor(ontology)));
        Assert.assertTrue(validationResult.valid());
    }

    @Test
    public void testNameMismatchQuery() {
        AsgStartEntityValidatorStrategy strategy = new AsgStartEntityValidatorStrategy();
        query.setOnt("bela");
        ValidationResult validationResult = strategy.apply(query, new AsgStrategyContext(new Ontology.Accessor(ontology)));
        Assert.assertFalse(validationResult.valid());
        Assert.assertEquals(Stream.ofAll(validationResult.errors()).toJavaArray(String.class)[0],AsgStartEntityValidatorStrategy.ERROR_1);
    }

    @Test
    public void testStartOnlyElementQuery() {
        AsgStartEntityValidatorStrategy strategy = new AsgStartEntityValidatorStrategy();
        ValidationResult validationResult = strategy.apply(AsgQuery.Builder.start("Q1", "Dragon").build(), new AsgStrategyContext(new Ontology.Accessor(ontology)));
        Assert.assertFalse(validationResult.valid());
        Assert.assertEquals(Stream.ofAll(validationResult.errors()).toJavaArray(String.class)[0],AsgStartEntityValidatorStrategy.ERROR_2);
    }

    @Test
    public void testWrongPositionStartElementQuery() {
        AsgQuery query = AsgQuery.Builder.start("Q1", "Dragons")
                .next(unTyped(1))
                .next(rel(2, OntologyTestUtils.OWN.getrType(), R).below(relProp(10,
                        RelProp.of(10, START_DATE.type, of(eq, new Date())))))
                .next(unTyped(3))
                .build();

        AsgQueryUtil.get(query.getStart(),3).get().addNextChild(new AsgEBase<>(new Start()));

        AsgStartEntityValidatorStrategy strategy = new AsgStartEntityValidatorStrategy();
        ValidationResult validationResult = strategy.apply(query, new AsgStrategyContext(new Ontology.Accessor(ontology)));
        Assert.assertFalse(validationResult.valid());
        Assert.assertEquals(Stream.ofAll(validationResult.errors()).toJavaArray(String.class)[0],AsgStartEntityValidatorStrategy.ERROR_3);
    }
}
