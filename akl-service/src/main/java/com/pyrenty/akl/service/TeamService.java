package com.pyrenty.akl.service;

import com.pyrenty.akl.domain.Season;
import com.pyrenty.akl.domain.Team;
import com.pyrenty.akl.domain.User;
import com.pyrenty.akl.repository.SeasonRepository;
import com.pyrenty.akl.repository.TeamRepository;
import com.pyrenty.akl.repository.UserRepository;
import com.pyrenty.akl.web.rest.errors.CustomParameterizedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class TeamService {
    @Inject
    private TeamRepository teamRepository;

    @Inject
    private UserService userService;

    @Inject
    private UserRepository userRepository;

    @Inject
    private SeasonRepository seasonRepository;

    @PreAuthorize("isAuthenticated()")
    public Team create(Team team) {
        team.setActivated(false);
        team.setSeason(seasonRepository.findByArchived(false));
        Team result = teamRepository.save(team);
        User currentUser = userService.getUserWithAuthorities();
        currentUser.setCaptain(result);
        userRepository.save(currentUser);

        return result;
    }

    @Transactional(readOnly = true)
    public Optional<Team> get(Long id) {
        return Optional.ofNullable(teamRepository.findOne(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Team> activate(Long id) {
        return Optional.ofNullable(teamRepository.findOne(id))
                .map(team -> {
                    if (team.getMembers().size() < 4) {
                        throw new CustomParameterizedException("Team doesn't have enough member");
                    }

                    team.setActivated(true);

                    // todo: Send team activated mail to members or captain

                    return teamRepository.save(team);
                });
    }

    public void delete(Long id) {
        Team team = teamRepository.findOne(id);
        if (team != null) {
            // Captain
            User captain = team.getCaptain();
            captain.setCaptain(null);
            userRepository.save(captain);

            // Members
            Set<User> members = team.getMembers().stream()
                    .map(m -> {
                        m.setMember(null);
                        return m;
                    })
                    .collect(Collectors.toSet());
            userRepository.save(members);

            // Standins
            Set<User> standins = team.getStandins().stream()
                    .map(s -> {
                        s.setStandin(null);
                        return s;
                    })
                    .collect(Collectors.toSet());
            userRepository.save(standins);

            // Team
            team.setCaptain(null);
            team.setMembers(null);
            team.setStandins(null);

            team.setRequests(null);
            teamRepository.delete(team);
        }
    }
}
