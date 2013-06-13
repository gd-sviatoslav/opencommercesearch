/*
* Licensed to OpenCommerceSearch under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. OpenCommerceSearch licenses this
* file to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.opencommercesearch.sample.store;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import atg.nucleus.GenericService;

/**
 * @author S.L. (slisenkin at griddynamics dot com) 11.06.2013 17:32:03
 */
public final class ATGLoggingUtil {
    
    /**
     * Format date as: yyyy-MM-dd__HH:mm:ss.SSS
     */
    public static final SimpleDateFormat FORMAT_DATE = createGmt("yyyy-MM-dd__HH:mm:ss.SSS");

    /**
     * Creates dates formatting utility with GMT time zone setting.
     * 
     * @param pattern date pattern
     * @return dates formatting utility with GMT time zone
     */
    public static SimpleDateFormat createGmt(final String pattern) {
      final SimpleDateFormat fmt = new SimpleDateFormat(pattern, Locale.ENGLISH);
      fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
      return fmt;
    }
    
    public static void debug(GenericService inst, String msg, Object... params) {
        if (inst.isLoggingDebug()) {
            inst.logDebug(MessageFormat.format(msg, params));
        }
    }
    
    public static void info(GenericService inst, String msg, Object... params) {
        if (inst.isLoggingInfo()) {
            inst.logInfo(MessageFormat.format(msg, params));
        }
    }

    public static void error(GenericService inst, String msg, Object... params) {
        if (inst.isLoggingError()) {
            inst.logError(MessageFormat.format(msg, params));
        }
    }

    public static void error(GenericService inst, Throwable t, String msg, Object... params) {
        if (inst.isLoggingError()) {
            inst.logError(MessageFormat.format(msg, params), t);
        }
    }

}
