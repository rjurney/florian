package com.yangdb.fuse.unipop.schemaProviders;

/*-
 * #%L
 * fuse-dv-unipop
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

import com.yangdb.fuse.unipop.schemaProviders.indexPartitions.IndexPartitions;
import com.yangdb.fuse.unipop.structure.*;


/**
 * Created by benishue on 23-Mar-17.
 */
public interface PhysicalIndexProvider {
    class Constant implements PhysicalIndexProvider {
        //region Constructors
        public Constant(IndexPartitions indexPartitions) {
            this.indexPartitions = indexPartitions;
        }
        //endregion

        //region PhysicalIndexProvider Implementation
        @Override
        public IndexPartitions getIndexPartitionsByLabel(String label, ElementType elementType) {
            return this.indexPartitions;
        }
        //endregion

        //region Fields
        private IndexPartitions indexPartitions;
        //endregion
    }

    IndexPartitions getIndexPartitionsByLabel(String label, ElementType elementType);
}
