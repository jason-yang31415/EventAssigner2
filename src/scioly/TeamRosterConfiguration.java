package scioly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import scioly.Team.TeamMember;
import scioly.Tournament.TournamentBlock;
import scioly.Tournament.TournamentEvent;

/**
 * Contains all configuration requirements for the roster assignment.
 * @author jason
 *
 */
public class TeamRosterConfiguration {

	private final Team team;
	private final Tournament tournament;
	private final int[] teamSizes;
	private int totalTeamSize;
	private HashMap<TeamMember, HashMap<TournamentBlock, ArrayList<TournamentEvent>>> conflicts = new HashMap<TeamMember, HashMap<TournamentBlock, ArrayList<TournamentEvent>>>();
	private HashMap<TeamMember, ArrayList<TournamentEvent>> signups = new HashMap<TeamMember, ArrayList<TournamentEvent>>();
	private TeamMember[] assignmentOrder;
	private ArrayList<TeamMember[]> stacks = new ArrayList<TeamMember[]>();
	private ArrayList<TeamMember[]> unstacks = new ArrayList<TeamMember[]>();

	public TeamRosterConfiguration(Team team, Tournament tournament, int[] teamSizes) {
		this.team = team;
		this.tournament = tournament;
		this.teamSizes = teamSizes;
		this.assignmentOrder = new TeamMember[team.getTeamMembers().size()];
		assignmentOrder = team.getTeamMembers().toArray(assignmentOrder);

		for (int i : teamSizes)
			totalTeamSize += i;

		computeConflicts();
		computeEventIndices();
		computeTeamMemberIndices();
	}

	public void addStack(TeamMember a, TeamMember b) {
		stacks.add(new TeamMember[] {a, b});
	}

	public void addUnstack(TeamMember a, TeamMember b) {
		unstacks.add(new TeamMember[] {a, b});
	}

	public ArrayList<TeamMember[]> getStacks(){
		return stacks;
	}

	public ArrayList<TeamMember[]> getUnstacks(){
		return unstacks;
	}

	public Team getTeam() {
		return team;
	}

	public Tournament getTournament() {
		return tournament;
	}

	public int[] getTeamSizes() {
		return teamSizes;
	}

	public int getTotalTeamSize() {
		return totalTeamSize;
	}

	/**
	 * Determines schedulign conflicts per team member
	 */
	private void computeConflicts() {
		for (TeamMember member : team.getTeamMembers()) {
			HashMap<TournamentBlock, ArrayList<TournamentEvent>> memberSchedule = new HashMap<TournamentBlock, ArrayList<TournamentEvent>>();
			ArrayList<TournamentEvent> memberSignups = new ArrayList<TournamentEvent>();
			for (TournamentEvent event : member.getEvents()) {
				if (event.getBlock().getTimeslot() != -1) {
					if (memberSchedule.containsKey(event.getBlock()))
						memberSchedule.get(event.getBlock()).add(event);
					else
						memberSchedule.put(event.getBlock(), new ArrayList<TournamentEvent>(Arrays.asList(new TournamentEvent[] {event})));
				} else {
					memberSignups.add(event);
				}
			}
			conflicts.put(member, memberSchedule);
			signups.put(member, memberSignups);
		}
	}

	/**
	 * Computes a unique array index for each event, for use in roster byte arrays
	 */
	private void computeEventIndices() {
		int counter = 0;
		for (TournamentEvent event : tournament.getEvents()) {
			event.setIndex(counter);
			counter++;
		}
	}

	/**
	 * Computes a unique array index for each team member, for use in roster byte arrays
	 */
	private void computeTeamMemberIndices() {
		int counter = 0;
		for (TeamMember member : team.getTeamMembers()) {
			member.setIndex(counter);
			counter++;
		}
	}

	/**
	 * Gets the conflicting events for a given team member at each scheduling block, not including signup events
	 * @param member
	 * @return
	 */
	public HashMap<TournamentBlock, ArrayList<TournamentEvent>> getConflicts(TeamMember member){
		return conflicts.get(member);
	}

	/**
	 * Gets a list of signup events for a given team member
	 * @param member
	 * @return
	 */
	public ArrayList<TournamentEvent> getSignups(TeamMember member){
		return signups.get(member);
	}

	/**
	 * Gets the TeamMember at a given index, used for assignment order (not the same as the array index of a TeamMember)
	 * @param index
	 * @return
	 */
	public TeamMember getTeamMemberAt(int index) {
		return assignmentOrder[index];
	}

}
