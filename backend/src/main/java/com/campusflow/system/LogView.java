package com.campusflow.system;

import java.time.Instant;
public record LogView(long id,String actor,String action,String target,String result,String reason,Instant occurredAt) {}
