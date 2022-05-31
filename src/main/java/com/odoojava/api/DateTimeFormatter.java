package com.odoojava.api;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateTimeFormatter {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final DateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        DATE_TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static String formatDateTimeForWrite(Object value) {
        return DATE_TIME_FORMAT.format(value);
    }

    public static String formatDateForWrite(Object value) {
        return DATE_FORMAT.format(value);
    }

    public static Date parseDate(Object value) {
        try {
            return DATE_FORMAT.parse(String.valueOf(value));
        } catch (ParseException e) {
            return null;
        }
    }

    public static Date parseDateTime(Object value) {
        try {
            return DATE_TIME_FORMAT.parse(String.valueOf(value));
        } catch (ParseException e) {
            return null;
        }
    }

}
