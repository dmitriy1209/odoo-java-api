/*
 *   Copyright 2011, 2013-2014 De Bortoli Wines Pty Limited (Australia)
 *   
 *   This file is part of OpenERPJavaAPI.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package com.odoojava.api;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides the session context object that is used in calls to the server.
 *
 * @author Pieter van der Merwe
 *
 */
public class Context extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;
    private final static String ACTIVE_TEST_TAG = "active_test";
    private final static String LANG_TAG = "lang";
    private final static String TIMEZONE_TAG = "tz";

    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
        // TODO Auto-generated method stub
        super.putAll(m);
    }

    /**
     * Gets the active_test context property.
     *
     * @return The active_test value or null if the property doesn't exist.
     */
    public Boolean getActiveTest() {
        return (!this.containsKey(ACTIVE_TEST_TAG)) ? null : 
                Boolean.parseBoolean(this.get(ACTIVE_TEST_TAG).toString());
    }

    /**
     * Sets the active_test context value. If true, only active items are
     * returned by default when calling the ReadObject item.
     *
     * @param active_test
     */
    public void setActiveTest(boolean active_test) {
        this.remove(ACTIVE_TEST_TAG);
        this.put(ACTIVE_TEST_TAG, active_test);
    }

    /**
     * Gets the 'lang' context value.
     *
     * @return Language or null if the property doesn't exist.
     */
    public String getLanguage() {
        return (!this.containsKey(LANG_TAG)) ? null : this.get(LANG_TAG).toString();
    }

    /**
     * Sets the 'lang' context value.
     *
     * @param lang Examples "en_US", "nl_NL"
     */
    public void setLanguage(String lang) {
        this.remove(LANG_TAG);
        this.put(LANG_TAG, lang);
    }

    /**
     * Gets the 'tz' context value.
     *
     * @return Time zone string or null if the property doesn't exist
     */
    public String getTimeZone() {
        if (!this.containsKey(TIMEZONE_TAG)) return null;
        if (this.get(TIMEZONE_TAG) instanceof Boolean && Boolean.getBoolean(this.get(TIMEZONE_TAG).toString()) == false) return null;
        return this.get(TIMEZONE_TAG).toString();
    }

    /**
     * Sets the 'tz' context flag.
     *
     * @param tz Examples "Australia/Sydney", "Europe/Brussels"
     */
    public void setTimeZone(String tz) {
        this.remove(TIMEZONE_TAG);
        this.put(TIMEZONE_TAG, tz);
    }
}
