/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.jobs.service.resource;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.kie.kogito.jobs.api.Job;
import org.kie.kogito.jobs.service.model.ScheduledJob;
import org.kie.kogito.jobs.service.model.ScheduledJob.ScheduledJobBuilder;
import org.kie.kogito.jobs.service.model.job.JobDetails;
import org.kie.kogito.jobs.service.model.job.ScheduledJobAdapter;
import org.kie.kogito.jobs.service.repository.ReactiveJobRepository;
import org.kie.kogito.jobs.service.scheduler.impl.TimerDelegateJobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Path(JobResource.JOBS_PATH)
public class JobResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobResource.class);
    @SuppressWarnings("squid:S1075")
    public static final String JOBS_PATH = "/jobs";

    @Inject
    TimerDelegateJobScheduler scheduler;

    @Inject
    ReactiveJobRepository jobRepository;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public CompletionStage<ScheduledJob> create(Job job) {
        LOGGER.debug("REST create {}", job);
        return ReactiveStreams.fromPublisher(scheduler.schedule(ScheduledJobAdapter.to(ScheduledJob.builder().job(job).build())))
                .map(ScheduledJobAdapter::of)
                .findFirst()
                .run()
                .thenApply(j -> j.orElseThrow(() -> new RuntimeException("Failed to schedule job " + job)));
    }

    @PATCH
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public CompletionStage<ScheduledJob> patch(@PathParam("id") String id, @RequestBody Job job) {
        LOGGER.debug("REST patch update {}", job);
        JobDetails jobToBeMerged = ScheduledJobAdapter.to(ScheduledJobBuilder.from(job));
        //validating allowed patch attributes
        if (Objects.nonNull(jobToBeMerged.getPayload())
                || StringUtils.isNotEmpty(jobToBeMerged.getId())
                || StringUtils.isNotEmpty(jobToBeMerged.getScheduledId())
                || StringUtils.isNotEmpty(jobToBeMerged.getCorrelationId())
                || (Objects.nonNull(jobToBeMerged.getExecutionCounter()) && jobToBeMerged.getExecutionCounter() > 0)
                || Objects.nonNull(jobToBeMerged.getPriority())
                || (Objects.nonNull(jobToBeMerged.getRetries()) && jobToBeMerged.getRetries() > 0)
                || Objects.nonNull(jobToBeMerged.getRecipient())
                || Objects.nonNull(jobToBeMerged.getStatus())) {
            throw new IllegalArgumentException("Patch an only be applied to the Job scheduling trigger attributes");
        }

        return scheduler.reschedule(id, jobToBeMerged.getTrigger())
                .map(ScheduledJobAdapter::of)
                .findFirst()
                .run()
                .thenApply(j -> j.orElseThrow(() -> new NotFoundException("Failed to reschedule job " + job)));
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public CompletionStage<ScheduledJob> delete(@PathParam("id") String id) {
        return scheduler
                .cancel(id)
                .thenApply(result -> Optional
                        .ofNullable(result)
                        .map(ScheduledJobAdapter::of)
                        .orElseThrow(() -> new NotFoundException("Failed to cancel job scheduling for jobId " + id)));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    public CompletionStage<ScheduledJob> get(@PathParam("id") String id) {
        return jobRepository
                .get(id)
                .thenApply(result -> Optional
                        .ofNullable(result)
                        .map(ScheduledJobAdapter::of)
                        .orElseThrow(() -> new NotFoundException("Job not found id " + id)));
    }
}