package scioly;

import java.util.ArrayList;
import java.util.List;

import constraintOptimizer.ConstraintOptimizer.BranchAndBound;
import scioly.Team.TeamMember;
import scioly.TeamRoster.TeamAssignment;
import scioly.TeamRoster.TeamRosterTeam;
import scioly.Tournament.TournamentEvent;
import util.Combinations;

/**
 *
 * FullTeamRoster
 * Represents a partial full roster assignment: like TeamRoster, it is partial (only stores a single person-event assignment combination);
 * 	the complete roster can be computed by traversing the assignment tree.
 * 
 * Since a TeamRoster assignment represents a person-team assignment combination but not a person-event assignment combination. Given a leaf TeamRoster,
 * 	we can traverse the TeamRoster tree back to the root and mimic the same person-team assignment combinations, except produces multiple branches depending
 * 	on specific event assignment combinations (but still restricted to the team specified in the corresponding TeamRoster).
 * 
 * FullTeamRoster implements branch() and lowerBound() for depth-first branch and bound optimization.
 * 
 * @author jason
 *
 */
public class FullTeamRoster implements BranchAndBound {

	private TeamRosterConfiguration configuration;
	private FullTeamRoster parent;
	private TeamRoster roster;

	private TeamRosterTeam[] teams;
	private byte[] eventNumberRemaining;
	private byte[] lowerBounds;

	private EventAssignment assignment;

	/**
	 * Generate a complete basic FullTeamRoster from a TeamRoster; the FullTeamRoster will only have team assignments, not event assignments.
	 * @param configuration
	 * @param roster
	 * @return a basic FullTeamRoster
	 */
	public static FullTeamRoster reconstruct(TeamRosterConfiguration configuration, TeamRoster roster) {
		FullTeamRoster fullRoster = initFullTeamRoster(configuration, roster);
		TeamRoster tr = roster;
		while (tr.getAssignment() != null) {
			if (tr.getAssignment().getTeamIndex() >= 0)
				fullRoster.assignMember(tr.getAssignment().getTeamIndex(), tr.getAssignment().getMember());
			tr = tr.getParent();
		}
		return fullRoster;
	}

	/**
	 * Generate a partial FullTeamRoster from a TeamRoster. Branching the partial FullTeamRoster produces children rosters assigned in the reverse order of
	 * the TeamRoster (i.e. by traversing down the FullTeamRoster tree, you traverse up the TeamRoster tree back toward the root).
	 * @param configuration
	 * @param roster
	 * @return a partial FullTeamRoster
	 */
	public static FullTeamRoster initFullTeamRoster(TeamRosterConfiguration configuration, TeamRoster roster) {
		TeamRosterTeam[] teams = new TeamRosterTeam[configuration.getTeamSizes().length];
		for (int i = 0; i < teams.length; i++){
			teams[i] = new TeamRosterTeam(configuration, configuration.getTeamSizes()[i]);
		}
		byte[] remaining = new byte[configuration.getTournament().getEvents().size()];
		for (TeamMember member : configuration.getTeam().getTeamMembers()) {
			for (TournamentEvent event : member.getEvents()) {
				remaining[event.getIndex()]++;
			}
		}
		return new FullTeamRoster(null, configuration, roster, teams, remaining, new byte[configuration.getTournament().getEvents().size()]);
	}

	public FullTeamRoster(FullTeamRoster parent, TeamRosterConfiguration configuration, TeamRoster roster, TeamRosterTeam[] teams, byte[] eventNumberRemaining, byte[] lowerBounds) {
		this.parent = parent;
		this.configuration = configuration;
		this.teams = teams;
		this.eventNumberRemaining = eventNumberRemaining;
		this.lowerBounds = lowerBounds;
		this.roster = roster;
	}

	public EventAssignment getAssignment() {
		return assignment;
	}

	private void assignMember(int teamIndex, TeamMember member) {
		if (teamIndex >= 0)
			teams[teamIndex].assignMember(member);
		for (TournamentEvent event : member.getEvents())
			eventNumberRemaining[event.getIndex()]--;
	}

	private void assignMemberEvents(int teamIndex, TeamMember member, List<TournamentEvent> events) {
		if (teamIndex >= 0) {
			for (TournamentEvent event : events)
				teams[teamIndex].assignMemberEvent(member, event);
		}
		this.assignment = new EventAssignment(member, teamIndex, events);
	}

	/**
	 * A FullTeamRoster is complete when its corresponding TeamRoster is the root (i.e. has no parent)
	 * 
	 * @return if FullTeamRoster is complete
	 */
	@Override
	public boolean isComplete() {
		return roster.getParent() == null;
	}

	@Override
	public FullTeamRoster getParent() {
		return parent;
	}

	@Override
	public int score() {
		int sum = 0;
		for (TeamRosterTeam team : teams) {
			sum += team.score();
		}
		return sum;
	}

	@Override
	public ArrayList<BranchAndBound> branch() {
		ArrayList<BranchAndBound> branches = new ArrayList<BranchAndBound>();
		TeamAssignment ra = roster.getAssignment();
		if (ra != null) {
			TeamMember person = ra.getMember();
			int teamIndex = ra.getTeamIndex();
			List<List<TournamentEvent>> personPossibilities = Combinations.getCombinations(new ArrayList<List<TournamentEvent>>(configuration.getConflicts(person).values()));
			for (List<TournamentEvent> combination : personPossibilities) {
				FullTeamRoster tr = this.copy(this, roster.getParent());
				tr.assignMember(teamIndex, person);
				combination.addAll(configuration.getSignups(person));
				tr.assignMemberEvents(teamIndex, person, combination);
				branches.add(tr);
			}
		} else {
			FullTeamRoster ftr = this.copy(this, roster.getParent());
			branches.add(ftr);
		}
		return branches;
	}

	@Override
	public int lowerBound() {
		if (assignment != null) {
			for (TournamentEvent event : assignment.getEvents()) {
				int[] num = new int[teams.length];
				for (int i = 0; i < teams.length; i++) {
					num[i] = teams[i].getEventNumber(event.getIndex()) - event.getSize();
				}
				int left = eventNumberRemaining[event.getIndex()];
				while (left > 0) {
					int minIndex = 0;
					for (int i = 0; i < teams.length; i++)
						if (num[i] < num[minIndex])
							minIndex = i;
					if (num[minIndex] < 0)
						num[minIndex]++;
					else break;
					left--;
				}
				byte sum = 0;
				for (int i = 0; i < teams.length; i++) {
					if (event.isBuilding()) {
						if (Math.abs(num[i]) >= 2)
							sum++;
					}
					else
						sum += num[i] * num[i];
				}
				lowerBounds[event.getIndex()] = sum;
			}
		}
		int sum = 0;
		for (TournamentEvent event : configuration.getTournament().getEvents()) {
			sum += lowerBounds[event.getIndex()];
		}
		return sum;
	}

	/**
	 * Create a copy of the FullTeamRoster, except with the given TeamRoster. Use this method to create a child node in the FullTeamRoster tree.
	 * 
	 * @param roster
	 * @return a copy of FullTeamRoster
	 */
	public FullTeamRoster copy(FullTeamRoster parent, TeamRoster roster) {
		TeamRosterTeam[] teamsCopy = new TeamRosterTeam[teams.length];
		for (int i = 0; i < teams.length; i++) {
			teamsCopy[i] = teams[i].copy();
		}
		return new FullTeamRoster(parent, configuration, roster, teamsCopy, eventNumberRemaining.clone(), lowerBounds.clone());
	}

	public void print() {
		for (TeamRosterTeam trt : teams) {
			System.out.println("team:");
		}
	}

	public static class EventAssignment {

		private TeamMember member;
		private int teamIndex;
		private List<TournamentEvent> events;

		public EventAssignment(TeamMember member, int teamIndex, List<TournamentEvent> events) {
			this.member = member;
			this.teamIndex = teamIndex;
			this.events = events;
		}

		public TeamMember getMember() {
			return member;
		}

		public int getTeamIndex() {
			return teamIndex;
		}

		public List<TournamentEvent> getEvents() {
			return events;
		}

	}

}
