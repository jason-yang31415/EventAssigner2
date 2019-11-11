package scioly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class Tournament {

	private HashMap<String, TournamentEvent> events = new HashMap<String, TournamentEvent>();
	private HashMap<Integer, TournamentBlock> schedule = new HashMap<Integer, TournamentBlock>();

	public Tournament(ArrayList<Integer> blocks) {
		for (int b : blocks) {
			schedule.put(b, new TournamentBlock(this, b));
		}
	}

	public void addEvent(String name, int timeslot, int size) throws ScheduleException {
		if (events.containsKey(name)) throw new ScheduleException(String.format("Tournament already has event %s", name));

		if (!schedule.containsKey(timeslot)) throw new ScheduleException(String.format("Tournament has no timeslot %d", timeslot));
		TournamentEvent e = new TournamentEvent(name, schedule.get(timeslot), size);
		schedule.get(timeslot).addEvent(e);
		events.put(name, e);
	}

	public TournamentEvent getEvent(String name) {
		return events.get(name);
	}

	public TournamentBlock getBlock(int timeslot) {
		return schedule.get(timeslot);
	}

	public Collection<TournamentEvent> getEvents(){
		return events.values();
	}

	public static class TournamentEvent {

		private final String name;
		private final TournamentBlock block;
		private final int size;
		private int index;
		private boolean building;

		public TournamentEvent(String name, TournamentBlock block, int size) {
			this.name = name;
			this.block = block;
			this.size = size;
			building = false;
		}

		public void setBuilding(boolean building) {
			this.building = building;
		}

		public boolean isBuilding() {
			return building;
		}

		public int getIndex() {
			return index;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		public String getName() {
			return name;
		}

		public TournamentBlock getBlock() {
			return block;
		}

		public int getSize() {
			return size;
		}

		@Override
		public String toString() {
			return name;
		}

	}

	public static class TournamentBlock {

		private final Tournament tournament;
		private final int timeslot;
		private ArrayList<TournamentEvent> eventList = new ArrayList<TournamentEvent>();

		public TournamentBlock(Tournament tournament, int timeslot) {
			this.tournament = tournament;
			this.timeslot = timeslot;
		}

		public void addEvent(TournamentEvent event) throws ScheduleException {
			if (eventList.contains(event)) throw new ScheduleException(String.format("Block %d already has event %s", getTimeslot(), event.getName()));

			if (event.getBlock() == this)
				eventList.add(event);
			else throw new ScheduleException(String.format("Event %s does not belong in block %d", event.getName(), timeslot));
		}

		public Tournament getTournament() {
			return tournament;
		}

		public int getTimeslot() {
			return timeslot;
		}

		public ArrayList<TournamentEvent> getEvents(){
			return eventList;
		}

		@Override
		public String toString() {
			return String.format("%d", timeslot);
		}

	}

	public static class ScheduleException extends Exception {

		public ScheduleException(String error) {
			super(error);
		}

	}

}
