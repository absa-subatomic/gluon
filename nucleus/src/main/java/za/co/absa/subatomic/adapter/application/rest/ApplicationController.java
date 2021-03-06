package za.co.absa.subatomic.adapter.application.rest;

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
import za.co.absa.subatomic.adapter.member.rest.TeamMemberResourceBaseAssembler;
import za.co.absa.subatomic.application.application.ApplicationService;
import za.co.absa.subatomic.infrastructure.application.view.jpa.ApplicationEntity;
import za.co.absa.subatomic.infrastructure.application.view.jpa.BitbucketRepositoryEmbedded;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
@RequestMapping("/applications")
@ExposesResourceFor(ApplicationResource.class)
public class ApplicationController {

    private ApplicationService applicationService;

    private ApplicationResourceAssembler assembler;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
        this.assembler = new ApplicationResourceAssembler();
    }

    @PostMapping
    ResponseEntity<ApplicationResource> create(
            @RequestBody ApplicationResource request) {

        if (request.getRequestConfiguration() == null) {
            request.setRequestConfiguration(false);
        }

        ApplicationEntity applicationEntity = applicationService
                .newApplication(request, request.getRequestConfiguration());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(applicationEntity.getApplicationId())
                .toUri();

        return ResponseEntity.created(location).build();
    }

    @PutMapping("/{id}")
    ResponseEntity<ApplicationResource> update(@PathVariable String id,
                                               @RequestBody ApplicationResource request) {

        if (StringUtils.isNotEmpty(request.getJenkinsFolder())) {
            applicationService.setApplicationJenkinsfolder(id, request.getJenkinsFolder());
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();

        return ResponseEntity.created(location).build();
    }

    @GetMapping("/{id}")
    ApplicationResource get(@PathVariable String id) {
        return assembler.toResource(applicationService.findByApplicationId(id));
    }

    @GetMapping
    Resources<ApplicationResource> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String applicationType,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String projectId) {
        List<ApplicationResource> applications = new ArrayList<>();

        if (StringUtils.isNoneBlank(name, projectId)) {
            applications.add(
                    assembler.toResource(applicationService
                            .findByNameAndProjectId(name, projectId)));
        } else if (StringUtils.isNotBlank(projectId)) {
            applications.addAll(
                    assembler.toResources(
                            applicationService
                                    .findByProjectId(projectId)));
        } else if (StringUtils.isNoneBlank(name, projectName)) {
            applications.add(
                    assembler.toResource(applicationService
                            .findByNameAndProjectName(name, projectName)));
        } else if (StringUtils.isNotBlank(projectName)) {
            applications.addAll(
                    assembler.toResources(
                            applicationService
                                    .findByProjectName(projectName)));
        }

        if (StringUtils.isNotBlank(applicationType)) {
            applications.addAll(
                    assembler.toResources(applicationService
                            .findByApplicationType(applicationType)));
        }

        if (StringUtils.isAllBlank(name, applicationType, projectName,
                projectId)) {
            applications.addAll(applicationService.findAll().stream()
                    .map(assembler::toResource).collect(Collectors.toList()));
        }

        return new Resources<>(applications,
                linkTo(ApplicationController.class).withRel("self"),
                linkTo(methodOn(ApplicationController.class).list(name,
                        applicationType, projectName, projectId))
                        .withRel("self"));
    }

    @DeleteMapping("/{id}")
    ResponseEntity delete(@PathVariable String id) {
        applicationService.deleteApplication(id);
        return ResponseEntity.accepted().build();
    }

    private class ApplicationResourceAssembler
            extends
            ResourceAssemblerSupport<ApplicationEntity, ApplicationResource> {

        public ApplicationResourceAssembler() {
            super(ApplicationController.class, ApplicationResource.class);
        }

        @Override
        public ApplicationResource toResource(ApplicationEntity entity) {
            if (entity != null) {
                ApplicationResource resource = createResourceWithId(
                        entity.getApplicationId(),
                        entity);
                resource.setApplicationId(entity.getApplicationId());
                resource.setName(entity.getName());
                resource.setDescription(entity.getDescription());
                resource.setJenkinsFolder(entity.getJenkinsFolder());
                resource.setApplicationType(entity.getApplicationType());
                resource.setProjectId(entity.getProject().getProjectId());
                resource.setCreatedAt(entity.getCreatedAt());
                resource.setCreatedBy(new TeamMemberResourceBaseAssembler()
                        .toResource(entity.getCreatedBy()));

                if (entity.getBitbucketRepository() != null) {
                    BitbucketRepositoryEmbedded bitbucketRepository = entity
                            .getBitbucketRepository();
                    resource.setBitbucketRepository(new BitbucketRepository(
                            bitbucketRepository.getBitbucketId(),
                            bitbucketRepository.getSlug(),
                            bitbucketRepository.getName(),
                            bitbucketRepository.getRepoUrl(),
                            bitbucketRepository.getRemoteUrl()));
                }

                return resource;
            } else {
                return null;
            }
        }
    }
}
