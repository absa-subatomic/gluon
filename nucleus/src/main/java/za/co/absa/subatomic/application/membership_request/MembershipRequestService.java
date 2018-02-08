package za.co.absa.subatomic.application.membership_request;

import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.absa.subatomic.domain.membership_request.NewMembershipRequest;
import za.co.absa.subatomic.domain.team.TeamMemberId;
import za.co.absa.subatomic.infrastructure.membership_request.view.jpa.MembershipRequestEntity;
import za.co.absa.subatomic.infrastructure.membership_request.view.jpa.MembershipRequestRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MembershipRequestService {

    private CommandGateway commandGateway;

    private MembershipRequestRepository requestRepository;

    public MembershipRequestService(CommandGateway commandGateway,
                                    MembershipRequestRepository requestRepository) {
        this.commandGateway = commandGateway;
        this.requestRepository = requestRepository;
    }

    public String newMembershipRequest(String requestByMemberId, String teamId) {

        return commandGateway.sendAndWait(
                new NewMembershipRequest(
                        teamId,
                        new TeamMemberId(requestByMemberId)),
                1,
                TimeUnit.SECONDS);
    }

    @Transactional(readOnly = true)
    public MembershipRequestEntity findById(String id) {
        return requestRepository.findById(Long.parseLong(id));
    }

    @Transactional(readOnly = true)
    public MembershipRequestEntity findByIdAndTeamId(String id, String teamId) {
        return requestRepository.findByIdAndTeamId(Long.parseLong(id), teamId);
    }

    @Transactional(readOnly = true)
    public List<MembershipRequestEntity> findByTeamId(String teamId) {
        return requestRepository.findByTeamId(teamId);
    }
}
