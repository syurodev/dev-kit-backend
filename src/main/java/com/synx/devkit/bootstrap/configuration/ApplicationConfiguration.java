package com.synx.devkit.bootstrap.configuration;

import com.synx.devkit.identity.application.port.in.AuthorizeSyncRequestUseCase;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionUseCase;
import com.synx.devkit.identity.application.port.out.AccountRepository;
import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.identity.application.service.AuthorizeSyncRequestService;
import com.synx.devkit.identity.application.service.EstablishSyncSessionService;
import com.synx.devkit.audit.application.port.out.AuditEventSink;
import com.synx.devkit.replication.application.port.in.PullReplicationUseCase;
import com.synx.devkit.replication.application.port.in.PushReplicationUseCase;
import com.synx.devkit.replication.application.port.out.EntityHeadRepository;
import com.synx.devkit.replication.application.port.out.ReplicationLock;
import com.synx.devkit.replication.application.port.out.ReplicationLogRepository;
import com.synx.devkit.replication.application.service.CursorCodec;
import com.synx.devkit.replication.application.service.OperationDigestService;
import com.synx.devkit.replication.application.service.PullReplicationService;
import com.synx.devkit.replication.application.service.PushReplicationService;
import com.synx.devkit.replication.application.service.ReplicationRequestValidator;
import com.synx.devkit.replication.domain.service.ArbitrationPolicy;
import com.synx.devkit.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for framework-free application services. */
@Configuration
public class ApplicationConfiguration {
    @Bean
    EstablishSyncSessionUseCase establishSyncSessionUseCase(
            AccountRepository accounts,
            DeviceRepository devices,
            AuditEventSink audit,
            TransactionRunner transactions,
            Clock clock) {
        return new EstablishSyncSessionService(accounts, devices, audit, transactions, clock);
    }

    @Bean
    AuthorizeSyncRequestUseCase authorizeSyncRequestUseCase(
            AccountRepository accounts,
            DeviceRepository devices) {
        return new AuthorizeSyncRequestService(accounts, devices);
    }

    @Bean
    ReplicationRequestValidator replicationRequestValidator() {
        return new ReplicationRequestValidator();
    }

    @Bean
    OperationDigestService operationDigestService() {
        return new OperationDigestService();
    }

    @Bean
    ArbitrationPolicy arbitrationPolicy() {
        return new ArbitrationPolicy();
    }

    @Bean
    CursorCodec cursorCodec() {
        return new CursorCodec();
    }

    @Bean
    PushReplicationUseCase pushReplicationUseCase(
            ReplicationRequestValidator validator,
            OperationDigestService digests,
            ArbitrationPolicy arbitration,
            ReplicationLock locks,
            ReplicationLogRepository log,
            EntityHeadRepository heads,
            AuditEventSink audit,
            TransactionRunner transactions,
            Clock clock) {
        return new PushReplicationService(
                validator, digests, arbitration, locks, log, heads, audit, transactions, clock);
    }

    @Bean
    PullReplicationUseCase pullReplicationUseCase(
            CursorCodec cursors,
            ReplicationLogRepository log,
            AuditEventSink audit,
            Clock clock) {
        return new PullReplicationService(cursors, log, audit, clock);
    }
}
