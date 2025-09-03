package com.yangdb.fuse.asg.strategy.propertyGrouping;

/*-
 * #%L
 * fuse-asg
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




import com.yangdb.fuse.asg.strategy.AsgStrategy;
import com.yangdb.fuse.model.asgQuery.AsgEBase;
import com.yangdb.fuse.model.asgQuery.AsgQuery;
import com.yangdb.fuse.model.asgQuery.AsgQueryUtil;
import com.yangdb.fuse.model.asgQuery.AsgStrategyContext;
import com.yangdb.fuse.model.query.EBase;
import com.yangdb.fuse.model.query.quant.Quant1;
import com.yangdb.fuse.model.query.quant.QuantType;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Quant1AllQuantGroupingAsgStrategy implements AsgStrategy {
    //region AsgStrategy Implementation
    @Override
    public void apply(AsgQuery query, AsgStrategyContext context) {
        AtomicBoolean hasWorkToDo = new AtomicBoolean(true);
        while(hasWorkToDo.get()) {
            hasWorkToDo.set(false);

            AsgQueryUtil.elements(query, Quant1.class).forEach(quant -> {
                if (quant.geteBase().getqType().equals(QuantType.all)) {
                    AsgQueryUtil.<Quant1, Quant1>nextAdjacentDescendants(quant, Quant1.class).forEach(childQuant -> {
                        if (childQuant.geteBase().getqType().equals(QuantType.all)) {
                            hasWorkToDo.set(true);
                            AsgQueryUtil.replaceParents(quant,childQuant);
                            AsgQueryUtil.remove(query,childQuant);
                        }
                    });
                }
            });
        }
    }
    //endregion
}
