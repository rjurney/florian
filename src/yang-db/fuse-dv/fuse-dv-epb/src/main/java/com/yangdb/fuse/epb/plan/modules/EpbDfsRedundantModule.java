package com.yangdb.fuse.epb.plan.modules;

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

import com.yangdb.fuse.dispatcher.epb.PlanExtensionStrategy;
import com.yangdb.fuse.epb.plan.extenders.M1.M1DfsRedundantPlanExtensionStrategy;
import com.yangdb.fuse.model.asgQuery.AsgQuery;
import com.yangdb.fuse.model.execution.plan.composite.Plan;
import com.typesafe.config.Config;

/**
 * Created by Roman on 22/05/2017.
 */
public class EpbDfsRedundantModule extends BaseEpbModule {
    //region Private Methods
    @Override
    protected Class<? extends PlanExtensionStrategy<Plan, AsgQuery>> planExtensionStrategy(Config config) {
        return M1DfsRedundantPlanExtensionStrategy.class;
    }
    //endregion
}
