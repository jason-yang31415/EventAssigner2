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

	private TeamRosterConfiguration configuration;
	private int threads = 4;

	public ConstraintOptimizer(TeamRosterConfiguration configuration) {
		this.configuration = configuration;
	}

	public HashSet<CompleteTeamRoster> optimize() {
		OptimizerGroup group1 = round1();

		System.out.println("\n========\n\nround 1 complete producing " + group1.getOutput().size() + " rosters with score " + group1.getMinBound()
		+ "\n\n========\n");

		ConcurrentLinkedDeque<Entry> queue = new ConcurrentLinkedDeque<Entry>();
		for (Entry e : group1.getOutput()) {
			FullTeamRoster ros = FullTeamRoster.initFullTeamRoster(configuration, (TeamRoster) e.getRoster());
			queue.push(new Entry(ros, ros.lowerBound()));
		}
		OptimizerGroup group2 = round2(queue);

		HashSet<CompleteTeamRoster> rosters = new HashSet<CompleteTeamRoster>();
		for (Entry e : group2.getOutput()) {
			rosters.add(CompleteTeamRoster.reconstruct(configuration, (FullTeamRoster) e.getRoster()));
		}

		System.out.println("\n========\n\nround 2 complete producing " + rosters.size() + " rosters with score " + group2.getMinBound()
		+ "\n\n========\n");
		Entry entryB = group2.getOutput().getFirst();
		CompleteTeamRoster.reconstruct(configuration, (FullTeamRoster) entryB.getRoster()).print();
		System.out.println("lower bound: " + ((FullTeamRoster) entryB.getRoster()).lowerBound());
		System.out.println("actual score: " + ((FullTeamRoster) entryB.getRoster()).score());

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
		TeamRoster roster = TeamRoster.initTeamRoster(configuration);
		queue.push(new Entry(roster, roster.lowerBound()));
		OptimizerGroup group = new OptimizerGroup(queue);
		ArrayList<OptimizerThread> threadList = new ArrayList<OptimizerThread>();
		for (int i = 0; i < threads; i++) {
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
		OptimizerGroup group = new OptimizerGroup(queue);
		ArrayList<OptimizerThread> threadList = new ArrayList<OptimizerThread>();
		for (int i = 0; i < threads; i++) {
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
		private CountDownLatch latch;
		private boolean[] stopped;
		private int minBound;

		public OptimizerGroup(Deque<Entry> queue) {
			this.queue = queue;
			output = new LinkedBlockingDeque<Entry>();
		}

		public void run(ArrayList<OptimizerThread> threads) {
			minBound = Integer.MAX_VALUE - 1;
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
					else if (nextBound <= group.getMinBound() + 1) {
						group.getOutput().push(nextEntry);
						int size = group.getOutput().size();
						if (size % Math.pow(10, Math.floor(Math.log10(size))) == 0)
							System.out.println(group.getOutput().size() + " rosters with score " + group.getMinBound());
					}
				}
				else if (nextEntry.getBound() <= group.getMinBound() + 1) {
					ArrayList<BranchAndBound> branches = nextRoster.branch();
					for (BranchAndBound nextNextRoster : branches) {
						int bound = nextNextRoster.lowerBound();

						if (bound <= group.getMinBound() + 1) {
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

}
