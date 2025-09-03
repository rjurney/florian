package com.yangdb.es.plugins.script.regex;

/*-
 *
 * es-plugin
 * %%
 * Copyright (C) 2016 - 2019 yangdb   ------ www.yangdb.org ------
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
 *
 */

import org.opensearch.index.fielddata.ScriptDocValues;
//import org.opensearch.script.AbstractSearchScript;
import org.opensearch.script.SearchScript;

import java.util.regex.Pattern;

/**
 * Created by Roman on 5/26/2018.
 */
public class RegexScript /*extends AbstractSearchScript */{
    //region Constructors
    RegexScript(String field, Pattern pattern) {
        this.field = field;
        this.pattern = pattern;
    }
    //endregion

    //region AbstractSearchScript Implementation
/*
    @Override
    public Object run() {
        ScriptDocValues<String> docValue = (ScriptDocValues<String>) doc().get(this.field);
        if (docValue.isEmpty()) {
            return false;
        }

        return this.pattern.matcher(docValue.get(0)).matches();
    }
*/

//    @Override
    public double runAsDouble() {
        return 0;
    }
    //endregion

    //region Fields
    private String field;
    private Pattern pattern;
    //endregion
}
