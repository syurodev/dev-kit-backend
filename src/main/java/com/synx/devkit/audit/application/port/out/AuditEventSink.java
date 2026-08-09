package com.synx.devkit.audit.application.port.out;

import com.synx.devkit.audit.domain.AuditEvent;

public interface AuditEventSink {
    void record(AuditEvent event);
}
