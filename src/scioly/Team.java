package scioly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import scioly.Tournament.TournamentEvent;

public class Team {

	private HashMap<String, TeamMember> people = new HashMap<String, TeamMember>();
	private HashMap<TournamentEvent, ArrayList<TeamMember>> events = new HashMap<TournamentEvent, ArrayList<TeamMember>>();

	public void addTeamMember(String name, ArrayList<TournamentEvent> events) {
		people.put(name, new TeamMember(name, events));
		for (TournamentEvent event : events) {
			if (this.events.containsKey(event)) this.events.get(event).add(people.get(name));
			else this.events.put(event, new ArrayList<TeamMember>(Arrays.asList(new TeamMember[] {people.get(name)})));
		}
	}

	public TeamMember getTeamMember(String name) {
		return people.get(name);
	}

	public Collection<TeamMember> getTeamMembers(){
		return people.values();
	}

	public ArrayList<TeamMember> getEventMembers(TournamentEvent event){
		if (events.containsKey(event))
			return events.get(event);
		return new ArrayList<TeamMember>();
	}

	public static class TeamMember {

		private final String name;
		private final ArrayList<TournamentEvent> events;
		private int index;

		public TeamMember(String name, ArrayList<TournamentEvent> events) {
			this.name = name;
			this.events = events;
		}

		public String getName() {
			return name;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		public int getIndex() {
			return index;
		}

		public ArrayList<TournamentEvent> getEvents(){
			return events;
		}

		@Override
		public String toString() {
			return name;
		}

	}

}
