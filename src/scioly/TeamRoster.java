package scioly;

import java.util.ArrayList;

import constraintOptimizer.ConstraintOptimizer.BranchAndBound;
import scioly.Team.TeamMember;
import scioly.Tournament.TournamentEvent;

/**
 * 
 * TeamRoster
 * Represents a partial basic roster assignment:
 *  - Partial: TeamRoster does not store an entire roster; instead, it stores a single assignment and a parent TeamRoster; the entire roster can be calculated by traversing the TeamRoster tree
 *  - Basic: TeamRoster does not deal with conflicts; it only assigns TeamMembers to TeamRosterTeams
 * 
 *  TeamRoster implements branch() and lowerBound() for depth-first branch and bound optimization.
 * 
 * @author jason
 *
 */
public class TeamRoster implements BranchAndBound {

	private TeamRoster parent;
	private TeamRosterConfiguration configuration;

	private TeamRosterTeam[] teams;
	private int assignmentIndex;
	private byte[] eventNumberRemaining;
	private byte[] lowerBounds;
	private byte[] teamAssignments;

	private TeamAssignment assignment;

	/**
	 * Returns the root of a TeamRoster tree representing a blank assignment.
	 * 
	 * @param configuration
	 * @return Root of TeamRoster tree (blank assignment)
	 */
	public static TeamRoster initTeamRoster(TeamRosterConfiguration configuration) {
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
		byte[] teamAssignments = new byte[configuration.getTeam().getTeamMembers().size()];
		for (int i = 0; i < teamAssignments.length; i++)
			teamAssignments[i] = -1;
		return new TeamRoster(null, configuration, teams, 0, remaining, new byte[configuration.getTournament().getEvents().size()], teamAssignments);
	}

	/**
	 * Create a new TeamRoster
	 * 
	 * @param parent
	 * @param configuration
	 * @param teams
	 * @param assignmentIndex index of the next TeamMember to be assigned
	 * @param eventNumberRemaining array representing the number of unassigned TeamMembers for each event; used for calculating lower bound
	 * @param lowerBounds array representing current lower bound for each event
	 * @param teamAssignments array representing which team each person is on
	 */
	public TeamRoster(TeamRoster parent, TeamRosterConfiguration configuration, TeamRosterTeam[] teams, int assignmentIndex, byte[] eventNumberRemaining, byte[] lowerBounds, byte[] teamAssignments) {
		this.parent = parent;
		this.configuration = configuration;
		this.assignmentIndex = assignmentIndex;
		this.teams = teams;
		this.eventNumberRemaining = eventNumberRemaining;
		this.lowerBounds = lowerBounds;
		this.teamAssignments = teamAssignments;
	}

	@Override
	public TeamRoster getParent() {
		return parent;
	}

	/**
	 * A TeamRoster is complete when there are no more spots left in the TeamRosterTeams or there are no more TeamMembers left to assign.
	 * 
	 * @return if the TeamRoster is complete
	 */
	@Override
	public boolean isComplete() {
		int totalAssigned = 0;
		for (TeamRosterTeam t : teams)
			totalAssigned += t.getNumberMembers();
		return totalAssigned == configuration.getTotalTeamSize() || assignmentIndex == configuration.getTeam().getTeamMembers().size();
	}

	private void assignMember(int teamIndex, TeamMember member) {
		if (teamIndex >= 0) {
			teams[teamIndex].assignMember(configuration.getTeamMemberAt(assignmentIndex));
		}
		this.assignment = new TeamAssignment(member, teamIndex);
		assignmentIndex++;
		for (TournamentEvent event : member.getEvents())
			eventNumberRemaining[event.getIndex()]--;
		teamAssignments[member.getIndex()] = (byte) teamIndex;
	}

	private void assignMemberEvent(int teamIndex, TeamMember member, TournamentEvent event) {
		teams[teamIndex].assignMemberEvent(member, event);
	}

	public TeamAssignment getAssignment() {
		return assignment;
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
	public int lowerBound() {
		if (assignment != null) {
			for (TournamentEvent event : assignment.getMember().getEvents()) {
				int[] num = new int[teams.length];
				for (int i = 0; i < teams.length; i++) {
					num[i] = teams[i].eventNumber[event.getIndex()] - event.getSize();
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

	@Override
	public ArrayList<BranchAndBound> branch() {
		ArrayList<BranchAndBound> branches = new ArrayList<BranchAndBound>();
		TeamMember person = configuration.getTeamMemberAt(assignmentIndex);
		for (int i = 0; i < teams.length; i++) {
			if (teams[i].getNumberMembers() >= teams[i].getMaxMembers())
				continue;

			// check if stacking and unstacking rules are satisfied
			boolean valid = true;
			for (TeamMember[] pair : configuration.getStacks()) {
				if (person == pair[0]) {
					if (teamAssignments[pair[1].getIndex()] != -1 && teamAssignments[pair[1].getIndex()] != i) {
						valid = false;
						break;
					}
				} else if (person == pair[1]) {
					if (teamAssignments[pair[0].getIndex()] != -1 && teamAssignments[pair[0].getIndex()] != i) {
						valid = false;
						break;
					}
				}
			}
			if (!valid)
				continue;

			for (TeamMember[] pair : configuration.getUnstacks()) {
				if (person == pair[0]) {
					if (teamAssignments[pair[1].getIndex()] != -1 && teamAssignments[pair[1].getIndex()] == i) {
						valid = false;
						break;
					}
				} else if (person == pair[1]) {
					if (teamAssignments[pair[0].getIndex()] != -1 && teamAssignments[pair[0].getIndex()] == i) {
						valid = false;
						break;
					}
				}
			}
			if (!valid)
				continue;

			TeamRoster tr = this.copy(this);
			tr.assignMember(i, person);
			for (TournamentEvent event : person.getEvents())
				tr.assignMemberEvent(i, person, event);
			branches.add(tr);
		}
		int totalAssigned = 0;
		for (TeamRosterTeam t : teams)
			totalAssigned += t.getNumberMembers();
		int numSkipsLeft = configuration.getTeam().getTeamMembers().size() - assignmentIndex - configuration.getTotalTeamSize() + totalAssigned;

		boolean valid = true;
		for (TeamMember[] pair : configuration.getStacks()) {
			if (person == pair[0] || person == pair[1]) {
				valid = false;
				break;
			}
		}

		if (numSkipsLeft > 0 && valid) {
			System.out.println(person);
			TeamRoster tr = this.copy(this);
			tr.assignMember(-1, person);
			branches.add(tr);
		}
		return branches;
	}

	public TeamRoster copy(TeamRoster parent) {
		TeamRosterTeam[] teamsCopy = new TeamRosterTeam[teams.length];
		for (int i = 0; i < teams.length; i++) {
			teamsCopy[i] = teams[i].copy();
		}
		return new TeamRoster(parent, configuration, teamsCopy, assignmentIndex, eventNumberRemaining.clone(), lowerBounds.clone(), teamAssignments.clone());
	}

	public void print() {
		System.out.println("unassigned: " + assignmentIndex);
		for (int i = 0; i < teams.length; i++) {
			System.out.printf("team %d:\n", i);
			teams[i].print();
		}
		System.out.printf("lower bound: %d\n", lowerBound());
		System.out.printf("score: %d\n", score());
		System.out.println();
	}

	public static class TeamAssignment {

		private TeamMember member;
		private int team;

		public TeamAssignment(TeamMember member, int team) {
			this.member = member;
			this.team = team;
		}

		public TeamMember getMember() {
			return member;
		}

		public int getTeamIndex() {
			return team;
		}

	}

	public static class TeamRosterTeam {

		private final TeamRosterConfiguration configuration;
		private int size;
		private int numberAssigned;
		private byte[] eventNumber;

		public TeamRosterTeam(TeamRosterConfiguration configuration, int size) {
			this.configuration = configuration;
			this.size = size;
			this.eventNumber = new byte[configuration.getTournament().getEvents().size()];
			this.numberAssigned = 0;
		}

		public TeamRosterTeam(TeamRosterConfiguration configuration, int size, int numberAssigned, byte[] eventNumber) {
			this.configuration = configuration;
			this.size = size;
			this.numberAssigned = numberAssigned;
			this.eventNumber = eventNumber;
		}

		public void assignMember(TeamMember member) {
			numberAssigned++;
		}

		public void assignMemberEvent(TeamMember member, TournamentEvent event) {
			eventNumber[event.getIndex()]++;
		}

		public int getNumberMembers() {
			return numberAssigned;
		}

		public int getMaxMembers() {
			return size;
		}

		public int getEventNumber(int index) {
			return eventNumber[index];
		}

		public int score() {
			int sum = 0;
			for (TournamentEvent event : configuration.getTournament().getEvents()) {
				int delta = eventNumber[event.getIndex()] - event.getSize();
				if (event.isBuilding()) {
					if (Math.abs(delta) >= 2)
						sum++;
				} else {
					sum += delta * delta;
				}
			}
			return sum;
		}

		public TeamRosterTeam copy() {
			return new TeamRosterTeam(configuration, size, numberAssigned, eventNumber.clone());
		}

		public void print() {
			System.out.println(eventNumber);
		}

	}

}
