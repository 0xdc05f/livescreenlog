package com.livescreenlog.app.dto;
import java.util.List;
public record StatsOverviewResponse(
  String projectKey,
  long liveActiveUsers,
  long liveActiveSessions,
  List<StatsBucket> hours,
  List<StatsBucket> days,
  List<StatsBucket> months
) {}