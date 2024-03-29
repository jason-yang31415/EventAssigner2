package constraintOptimizer;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;

import scioly.CompleteTeamRoster;
import scioly.FullTeamRoster;
import scioly.TeamRoster;
import scioly.TeamRosterConfiguration;

/**
 * 
 * ConstraintOptimizer
 * Does depth-first branch and bound optimization to find the best event assignments. The methodology consists of 2 rounds of DFBnB.
 * In round 1, the TeamRoster tree is explored to find the optimum team assignments as a heuristic for optimal event assignment. The TeamRoster
 * tree only considers which balancing the number of people on each event between the teams, and does not consider actual assignments, which
 * may vary due to conflicts.
 * In round 2, the optimum TeamRoster assignments from round 1 are used as an input to explore the FullTeamRoster tree, which considers conflicts.
 * The best assignments from round 2 are then outputted.
 * 
 * @author jason
 *
 */
public class ConstraintOptimizer {

	private OptimizerConfiguration optConfig;
	private TeamRosterConfiguration teamConfig;

	public ConstraintOptimizer(OptimizerConfiguration optConfig, TeamRosterConfiguration teamConfig) {
		this.optConfig = optConfig;
		this.teamConfig = teamConfig;
	}

	public HashSet<CompleteTeamRoster> optimize() {
		OptimizerGroup group1 = round1();
		ArrayList<BranchAndBound> output1 = group1.getOptimal();

		System.out.println("\n========\n\nround 1 complete producing " + output1.size() + " rosters with score " + group1.getMinBound()
		+ " (+" + group1.getTolerance() + ")\n\n========\n");

		ConcurrentLinkedDeque<Entry> queue = new ConcurrentLinkedDeque<Entry>();
		for (BranchAndBound e : output1) {
			FullTeamRoster ros = FullTeamRoster.initFullTeamRoster(teamConfig, (TeamRoster) e);
			queue.push(new Entry(ros, ros.lowerBound()));
		}
		OptimizerGroup group2 = round2(queue);
		ArrayList<BranchAndBound> output2 = group2.getOptimal();

		HashSet<CompleteTeamRoster> rosters = new HashSet<CompleteTeamRoster>();
		for (BranchAndBound e : output2) {
			rosters.add(CompleteTeamRoster.reconstruct(teamConfig, (FullTeamRoster) e));
		}

		System.out.println("\n========\n\nround 2 complete producing " + rosters.size() + " rosters with score " + group2.getMinBound()
		+ " (+" + group2.getTolerance() + ")\n\n========\n");
		FullTeamRoster first = (FullTeamRoster) output2.get(0);
		CompleteTeamRoster.reconstruct(teamConfig, first).print();
		System.out.println("lower bound: " + first.lowerBound());
		System.out.println("actual score: " + first.score());

		System.out.println();
		System.out.println(" + " + (rosters.size() - 1) + " more rosters...");

		return rosters;
	}

	/**
	 * Round 1 of optimizations. Traverse the TeamRoster tree and returns an OptimizerGroup containing the results.
	 * @return OptimizerGroup containing round 1 results
	 */
	private OptimizerGroup round1() {
		ConcurrentLinkedDeque<Entry> queue = new ConcurrentLinkedDeque<Entry>();
		TeamRoster roster = TeamRoster.initTeamRoster(teamConfig);
		queue.push(new Entry(roster, roster.lowerBound()));
		OptimizerGroup group = new OptimizerGroup(queue, optConfig.getTolerance1());
		ArrayList<OptimizerThread> threadList = new ArrayList<OptimizerThread>();
		for (int i = 0; i < optConfig.getThreads(); i++) {
			threadList.add(new OptimizerThreadA(group, i));
		}
		group.run(threadList);

		try {
			group.getLatch().await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return group;
	}

	/**
	 * Round 2 of optimizations. Takes an input from round 1 and returns an OptimizerGroup containing the results
	 * @param queue queue of inputs
	 * @return OptimizerGroup containing round 2 results
	 */
	private OptimizerGroup round2(Deque<Entry> queue) {
		OptimizerGroup group = new OptimizerGroup(queue, optConfig.getTolerance2());
		ArrayList<OptimizerThread> threadList = new ArrayList<OptimizerThread>();
		for (int i = 0; i < optConfig.getThreads(); i++) {
			threadList.add(new OptimizerThreadA(group, i));
		}
		group.run(threadList);
		try {
			group.getLatch().await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return group;
	}

	/**
	 * 
	 * OptimizerGroup
	 * Synchronizes a group of optimizer threads
	 * 
	 * @author jason
	 *
	 */
	private static class OptimizerGroup {

		private Deque<Entry> queue;
		private LinkedBlockingDeque<Entry> output;
		private int tolerance;
		private CountDownLatch latch;
		private boolean[] stopped;
		private int minBound;

		public OptimizerGroup(Deque<Entry> queue, int tolerance) {
			this.queue = queue;
			this.tolerance = tolerance;
			output = new LinkedBlockingDeque<Entry>();
		}

		public void run(ArrayList<OptimizerThread> threads) {
			minBound = Integer.MAX_VALUE - tolerance;
			stopped = new boolean[threads.size()];
			latch = new CountDownLatch(threads.size());
			for (OptimizerThread ot : threads) {
				new Thread(ot).start();
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		public Deque<Entry> getQueue(){
			return queue;
		}

		public LinkedBlockingDeque<Entry> getOutput(){
			return output;
		}

		public ArrayList<BranchAndBound> getOptimal(){
			ArrayList<BranchAndBound> optimal = new ArrayList<BranchAndBound>();
			for (Entry e : getOutput()) {
				if (e.getBound() <= getMinBound() + tolerance)
					optimal.add(e.getRoster());
			}
			return optimal;
		}

		private synchronized boolean done() {
			for (boolean b : stopped)
				if (!b)
					return false;
			return true;
		}

		private void setStopped(int index, boolean stopped) {
			this.stopped[index] = stopped;
		}

		private int getMinBound() {
			return minBound;
		}

		private void setMinBound(int minBound) {
			this.minBound = minBound;
		}

		private int getTolerance() {
			return tolerance;
		}

		private CountDownLatch getLatch() {
			return latch;
		}

	}

	private static abstract class OptimizerThread implements Runnable {

		protected OptimizerGroup group;
		protected final int index;

		public OptimizerThread(OptimizerGroup group, int index) {
			this.group = group;
			this.index = index;
		}

	}

	private static class OptimizerThreadA extends OptimizerThread {

		public OptimizerThreadA(OptimizerGroup group, int index) {
			super(group, index);
		}

		@Override
		public void run() {
			Entry nextEntry;
			while ((nextEntry = group.getQueue().poll()) != null || !group.done()) {
				if (nextEntry == null) {
					group.setStopped(index, true);
					continue;
				}
				else
					group.setStopped(index, false);

				BranchAndBound nextRoster = nextEntry.getRoster();

				if (nextRoster.isComplete()) {
					int nextBound = nextRoster.score();

					if (nextBound < group.getMinBound()) {
						group.getOutput().clear();
						group.getOutput().push(nextEntry);
						group.setMinBound(nextBound);
						System.out.println("\nnew minimum score found: " + nextBound);
					}
					else if (nextBound <= group.getMinBound() + group.getTolerance()) {
						group.getOutput().push(nextEntry);
						int size = group.getOutput().size();
						if (size % Math.pow(10, Math.floor(Math.log10(size))) == 0)
							System.out.println(group.getOutput().size() + " rosters with score " + group.getMinBound());
					}
				}
				else if (nextEntry.getBound() <= group.getMinBound() + group.getTolerance()) {
					ArrayList<BranchAndBound> branches = nextRoster.branch();
					for (BranchAndBound nextNextRoster : branches) {
						int bound = nextNextRoster.lowerBound();

						if (bound <= group.getMinBound() + group.getTolerance()) {
							group.getQueue().push(new Entry(nextNextRoster, bound));
						}
					}
				}
			}
			group.getLatch().countDown();
		}

	}

	/**
	 * Represents an assignment and its corresponding lower bound so the lower bound does not have to be recalculated.
	 * @author jason
	 *
	 */
	public static class Entry {

		private BranchAndBound roster;
		private int bound;

		public Entry(BranchAndBound roster, int bound) {
			this.roster = roster;
			this.bound = bound;
		}

		public BranchAndBound getRoster() {
			return roster;
		}

		public int getBound() {
			return bound;
		}

	}

	public static interface BranchAndBound {

		/**
		 * 
		 * @return if assignment is a leaf
		 */
		public boolean isComplete();

		/**
		 * 
		 * @return parent assignment in assignment tree
		 */
		public BranchAndBound getParent();

		/**
		 * Branches a partial assignment into children assignments
		 * 
		 * @return ArrayList containing children assignments
		 */
		public ArrayList<BranchAndBound> branch();

		/**
		 * Computes a lower bound on the score for the subtree
		 * 
		 * @return lower bound score
		 */
		public int lowerBound();

		/**
		 * Computes the actual score of a leaf assignment
		 * 
		 * @return score
		 */
		public int score();

	}

	public static class OptimizerConfiguration {

		private int threads;
		private int tolerance1;
		private int tolerance2;

		public OptimizerConfiguration(int threads, int tolerance1, int tolerance2) {
			this.threads = threads;
			this.tolerance1 = tolerance1;
			this.tolerance2 = tolerance2;
		}

		public int getThreads() {
			return threads;
		}

		public int getTolerance1() {
			return tolerance1;
		}

		public int getTolerance2() {
			return tolerance2;
		}

	}

}
