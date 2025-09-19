package openevolve;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


public class StudioConstants {

	public static final ZoneOffset TIMEZONE = ZoneOffset.UTC;
	public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
	public static final DateTimeFormatter FORMATTER =
			DateTimeFormatter.ofPattern(DATE_FORMAT).withZone(ZoneOffset.UTC);

}
