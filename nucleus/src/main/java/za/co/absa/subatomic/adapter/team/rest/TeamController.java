package za.co.absa.subatomic.adapter.team.rest;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.hateoas.ExposesResourceFor;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ResourceAssemblerSupport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;
import za.co.absa.subatomic.adapter.member.rest.TeamMemberResourceBase;
import za.co.absa.subatomic.adapter.member.rest.TeamMemberResourceBaseAssembler;
import za.co.absa.subatomic.application.team.TeamService;
import za.co.absa.subatomic.infrastructure.team.view.jpa.TeamEntity;
import za.co.absa.subatomic.infrastructure.team.view.jpa.TeamPersistenceHandler;

@RestController
@RequestMapping("/teams")
@ExposesResourceFor(TeamResource.class)
@Slf4j
public class TeamController {

    private final TeamResourceAssembler assembler;

    private TeamService teamService;
    private TeamPersistenceHandler teamPersistenceHandler;

    public TeamController(TeamService teamService, TeamPersistenceHandler teamPersistenceHandler) {
        this.teamService = teamService;
        this.teamPersistenceHandler = teamPersistenceHandler;
        this.assembler = new TeamResourceAssembler();
    }

    @PostMapping
    ResponseEntity<TeamResource> create(@RequestBody TeamResource request) {
        String teamSlackChannel = null;
        if (request.getSlack() != null) {
            teamSlackChannel = request.getSlack().getTeamChannel();
        }

        TeamEntity newTeam = teamService.newTeamFromSlack(request.getName(),
                request.getDescription(), request.getOpenShiftCloud(),
                request.getCreatedBy(),
                teamSlackChannel);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(newTeam.getTeamId())
                .toUri();

        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{id}/members/{memberId}")
    ResponseEntity<TeamResource> update(@PathVariable String id,
                                        @PathVariable String memberId,
                                        @RequestBody TeamResource request) {
        log.info("Trying to update Team with: {}", request);
        if (!request.getOwners().isEmpty() || !request.getMembers().isEmpty()) {

            teamService.addTeamMembers(id,
                    request.getCreatedBy(),
                    request.getOwners().stream()
                            .map(TeamMemberResourceBase::getMemberId)
                            .collect(toList()),
                    request.getMembers().stream()
                            .map(TeamMemberResourceBase::getMemberId)
                            .collect(toList()));
        }

        if (request.getSlack() != null) {
            teamService.addSlackIdentity(id,
                    memberId,
                    request.getSlack().getTeamChannel());
        }

        if (request.getDevOpsEnvironment() != null) {
            teamService.newDevOpsEnvironment(id,
                    request.getDevOpsEnvironment().getRequestedBy());
        }

        if (request.getMembershipRequests() != null) {
            for (MembershipRequestResource membershipRequest : request
                    .getMembershipRequests()) {
                if (StringUtils
                        .isNotBlank(membershipRequest.getMembershipRequestId())
                        &&
                        membershipRequest.getRequestStatus() != null &&
                        membershipRequest.getApprovedBy() != null) {
                    log.info(
                            "Updating membership request with approval status: {}",
                            membershipRequest.getRequestStatus());
                    teamService.updateMembershipRequest(id, membershipRequest);
                } else if (StringUtils
                        .isBlank(membershipRequest.getMembershipRequestId())
                        && membershipRequest.getRequestedBy() != null) {
                    teamService.newMembershipRequest(id,
                            membershipRequest.getRequestedBy().getMemberId());
                }
            }
        }

        if (StringUtils.isNoneBlank(request.getOpenShiftCloud(),
                request.getCreatedBy())) {
            teamService.updateTeamOpenShiftCloud(id,
                    request.getOpenShiftCloud(), request.getCreatedBy());
        }

        return ResponseEntity.accepted()
                .body(assembler.toResource(
                        teamPersistenceHandler.findByTeamId(id)));
    }

    @GetMapping("/{id}")
    TeamResource get(@PathVariable String id) {
        return assembler.toResource(
                teamPersistenceHandler.findByTeamId(id));
    }

    @GetMapping
    Resources<TeamResource> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String slackScreenName,
            @RequestParam(required = false) String slackTeamChannel,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String memberId) {
        Set<TeamResource> teams = new HashSet<>();

        // TODO see if we can't use that functional library for Java that has pattern matching?
        if (StringUtils.isNotBlank(name)) {
            teams.add(
                    assembler.toResource(teamPersistenceHandler
                            .findByName(name)));
        } else if (StringUtils.isNotBlank(slackScreenName)) {
            teams.addAll(teamPersistenceHandler
                    .findByMemberOrOwnerSlackScreenName(
                            slackScreenName)
                    .stream()
                    .map(assembler::toResource).collect(Collectors.toList()));
        } else if (StringUtils.isNotBlank(memberId)) {
            teams.addAll(teamPersistenceHandler
                    .findByMemberOrOwnerMemberId(
                            memberId)
                    .stream()
                    .map(assembler::toResource).collect(Collectors.toList()));
        } else if (StringUtils.isNotBlank(slackTeamChannel)) {
            teams.addAll(teamPersistenceHandler
                    .findBySlackTeamChannel(
                            slackTeamChannel)
                    .stream()
                    .map(assembler::toResource).collect(Collectors.toList()));
        } else if (StringUtils.isNotBlank(projectId)) {
            teams.addAll(teamService.findTeamsAssociatedToProject(
                    projectId).stream()
                    .map(assembler::toResource).collect(Collectors.toList()));
        } else if (StringUtils.isAllBlank(name, slackScreenName, slackTeamChannel,
                projectId)) {
            teams.addAll(teamPersistenceHandler.findAll().stream()
                    .map(assembler::toResource).collect(Collectors.toList()));
        }

        return new Resources<>(teams,
                linkTo(TeamController.class).withRel("self"),
                linkTo(methodOn(TeamController.class).list(name,
                        slackScreenName, slackTeamChannel, projectId, memberId))
                        .withRel("self"));
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    ResponseEntity delete(@PathVariable String teamId,
                          @PathVariable String memberId,
                          @RequestParam("requestedById") String requestedById) {

        log.info("Trying to delete member " + memberId + " from team " + teamId
                + " by requestor " + requestedById);

        if (!teamId.isEmpty() && !memberId.isEmpty()
                && !requestedById.isEmpty()) {
            teamService.removeTeamMember(teamId, memberId, requestedById);
            return ResponseEntity.accepted().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    private class TeamResourceAssembler
            extends ResourceAssemblerSupport<TeamEntity, TeamResource> {

        public TeamResourceAssembler() {
            super(TeamController.class, TeamResource.class);
        }

        @Override
        public TeamResource toResource(TeamEntity entity) {
            if (entity != null) {
                TeamResource resource = createResourceWithId(entity.getTeamId(),
                        entity);
                resource.setTeamId(entity.getTeamId());
                resource.setName(entity.getName());
                resource.setDescription(entity.getDescription());
                resource.setOpenShiftCloud(entity.getOpenShiftCloud());
                resource.setCreatedAt(entity.getCreatedAt());
                resource.setCreatedBy(entity.getCreatedBy().getMemberId());


                resource.getOwners()
                        .addAll(new TeamMemberResourceBaseAssembler()
                                .toResources(entity.getOwners()));
                ofNullable(entity.getMembers()).ifPresent(
                        members -> resource.getMembers()
                                .addAll(new TeamMemberResourceBaseAssembler()
                                        .toResources(members)
                                ));
                ofNullable(entity.getSlackDetails())
                        .ifPresent(slackDetails -> resource
                                .setSlack(
                                        new Slack(slackDetails
                                                .getTeamChannel())));
                return resource;
            } else {
                return null;
            }
        }
    }
}
