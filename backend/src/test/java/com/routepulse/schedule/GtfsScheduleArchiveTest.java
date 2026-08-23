package com.routepulse.schedule;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsScheduleArchiveTest {

  @Test
  void parsesQuotedFieldsAndTimesAfterMidnight() throws Exception {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("routes.txt", """
        route_id,route_short_name,route_long_name,route_type,route_color,route_text_color
        10,FTB,"Football Shuttle (""Red Terror"")",3,843C39,FFFFFF
        """);
    files.put("stops.txt", """
        stop_id,stop_code,stop_name,stop_lat,stop_lon,location_type,wheelchair_boarding
        20,9924,Avent Ferry Rd,35.77,-78.68,,0
        """);
    files.put("trips.txt", """
        route_id,service_id,trip_id,trip_headsign,direction_id,shape_id
        10,weekday,30,Downtown,,shape-1
        """);
    files.put("stop_times.txt", """
        trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type,timepoint,shape_dist_traveled
        30,25:01:02,25:01:30,20,1,,,1,42.5
        """);
    files.put("calendar.txt", """
        service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
        weekday,1,1,1,1,1,0,0,20260101,20261231
        """);

    GtfsScheduleArchive.ScheduleData schedule = GtfsScheduleArchive.read(zip(files));

    assertThat(schedule.routes()).singleElement()
        .extracting(GtfsScheduleArchive.Route::longName)
        .isEqualTo("Football Shuttle (\"Red Terror\")");
    assertThat(schedule.stopTimes()).singleElement()
        .satisfies(stopTime -> {
          assertThat(stopTime.arrivalSeconds()).isEqualTo(90_062);
          assertThat(stopTime.departureSeconds()).isEqualTo(90_090);
        });
  }

  @Test
  void convertsGtfsTimeToSeconds() {
    assertThat(GtfsScheduleArchive.parseGtfsTime("07:01:05")).isEqualTo(25_265);
    assertThat(GtfsScheduleArchive.parseGtfsTime("")).isNull();
  }

  private static byte[] zip(Map<String, String> files) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      for (Map.Entry<String, String> file : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(file.getKey()));
        zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }
}
