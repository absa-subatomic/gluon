package za.co.absa.subatomic.application.application;

import java.text.MessageFormat;
import java.util.Collection;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import za.co.absa.subatomic.application.team.TeamAssertions;
import za.co.absa.subatomic.domain.application.ApplicationInterface;
import za.co.absa.subatomic.domain.exception.DuplicateRequestException;
import za.co.absa.subatomic.infrastructure.application.ApplicationAutomationHandler;
import za.co.absa.subatomic.infrastructure.application.view.jpa.ApplicationEntity;
import za.co.absa.subatomic.infrastructure.application.view.jpa.ApplicationPersistenceHandler;
import za.co.absa.subatomic.infrastructure.application.view.jpa.ApplicationRepository;
import za.co.absa.subatomic.infrastructure.member.view.jpa.TeamMemberEntity;
import za.co.absa.subatomic.infrastructure.member.view.jpa.TeamMemberPersistenceHandler;
import za.co.absa.subatomic.infrastructure.project.view.jpa.ProjectEntity;
import za.co.absa.subatomic.infrastructure.project.view.jpa.ProjectPersistenceHandler;
import za.co.absa.subatomic.infrastructure.project.view.jpa.ProjectRepository;
import za.co.absa.subatomic.infrastructure.team.view.jpa.TeamEntity;

@Service
public class ApplicationService {

    private ApplicationRepository applicationRepository;

    private ProjectRepository projectRepository;

    private ApplicationPersistenceHandler applicationPersistenceHandler;

    private ProjectPersistenceHandler projectPersistenceHandler;

    private TeamMemberPersistenceHandler teamMemberPersistenceHandler;

    private ApplicationAutomationHandler applicationAutomationHandler;

    private TeamAssertions teamAssertions = new TeamAssertions();

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ProjectRepository projectRepository,
            ApplicationPersistenceHandler applicationPersistenceHandler,
            ProjectPersistenceHandler projectPersistenceHandler,
            TeamMemberPersistenceHandler teamMemberPersistenceHandler,
            ApplicationAutomationHandler applicationAutomationHandler) {
        this.applicationRepository = applicationRepository;
        this.projectRepository = projectRepository;
        this.applicationPersistenceHandler = applicationPersistenceHandler;
        this.projectPersistenceHandler = projectPersistenceHandler;
        this.teamMemberPersistenceHandler = teamMemberPersistenceHandler;
        this.applicationAutomationHandler = applicationAutomationHandler;
    }

    public ApplicationEntity newApplication(
            ApplicationInterface newApplication,
            boolean configurationRequested) {
        ApplicationEntity existingApplication = this.applicationPersistenceHandler
                .findByNameAndProjectProjectId(newApplication.getName(),
                        newApplication.getProjectId());

        if (existingApplication != null) {
            throw new DuplicateRequestException(MessageFormat.format(
                    "Application with name {0} already exists in project with id {1}.",
                    newApplication.getName(), newApplication.getProjectId()));
        }

        Collection<TeamEntity> teamsAssociatedToProject = this.projectPersistenceHandler
                .findTeamsAssociatedToProject(newApplication.getProjectId());

        TeamMemberEntity createdBy = this.teamMemberPersistenceHandler
                .findByTeamMemberId(
                        newApplication.getCreatedBy().getMemberId());

        teamAssertions.memberBelongsToAnyTeam(createdBy,
                teamsAssociatedToProject);

        ApplicationEntity applicationEntity = this.applicationPersistenceHandler
                .createApplication(newApplication);

        this.applicationAutomationHandler.applicationCreated(applicationEntity,
                configurationRequested);

        return applicationEntity;
    }

    @Transactional
    public void deleteApplication(String applicationId) {
        ApplicationEntity applicationEntity = applicationRepository
                .findByApplicationId(applicationId);
        ProjectEntity projectEntity = applicationEntity.getProject();
        projectEntity.getApplications().remove(applicationEntity);
        projectRepository.save(projectEntity);
        applicationRepository.deleteByApplicationId(applicationId);
    }

    public ApplicationPersistenceHandler getApplicationPersistenceHandler() {
        return applicationPersistenceHandler;
    }
}
