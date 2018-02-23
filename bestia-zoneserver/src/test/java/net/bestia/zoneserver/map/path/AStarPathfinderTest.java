package net.bestia.zoneserver.map.path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import bestia.model.geometry.Point;

/**
 * Testing on a map like the following:
 * 
 * <pre>
 *  0 x x
 *  0 x x
 *  0 x x
 * </pre>
 * 
 * @author Thomas
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class AStarPathfinderTest {

	private AStarPathfinder<Point> finder;

	private HeuristicEstimator<Point> estimator = new PointEstimator();

	@Mock
	private NodeProvider<Point> provider;

	private final static Node<Point> START = new Node<Point>(new Point(0, 0));
	private final static Node<Point> END_BLOCKED = new Node<Point>(new Point(2, 2));
	private final static Node<Point> END = new Node<Point>(new Point(0, 2));

	@Before
	public void setup() {
		
		final Node<Point> n1 = new Node<Point>(new Point(0, 1));
		final Node<Point> n2 = new Node<Point>(new Point(0, 2));

		when(provider.getConnectedNodes(any())).thenAnswer(new Answer<Set<Node<Point>>>() {

			@Override
			public Set<Node<Point>> answer(InvocationOnMock invocation) throws Throwable {
				
				Node<Point> arg = invocation.getArgument(0);
				
				if(arg.equals(START)) {
					return setOf(new Point(0, 1));
				} else if(arg.equals(n1)) {
					return setOf(new Point(0, 0), new Point(0, 2));
				} else if(arg.equals(n2)) {
					return setOf(new Point(0, 1));
				}
				
				return Collections.emptySet();
			}
		});
		
		finder = new AStarPathfinder<>(provider, estimator);
	}

	private Set<Node<Point>> setOf(Point... points) {
		// Points to nodes.
		final List<Node<Point>> nodes = Arrays.asList(points)
				.stream()
				.map(x -> new Node<>(x))
				.collect(Collectors.toList());

		return new HashSet<>(nodes);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullNodeProvider_throws() {
		new AStarPathfinder<>(null, estimator);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullEstimator_throws() {
		new AStarPathfinder<>(provider, null);
	}

	@Test(expected = NullPointerException.class)
	public void findPath_1argNull_throws() {
		finder.findPath(null, END);
	}

	@Test(expected = NullPointerException.class)
	public void findPath_2argNull_throws() {
		finder.findPath(START, null);
	}

	@Test
	public void findPath_noWayExists_empty() {
		List<Node<Point>> path = finder.findPath(START, END_BLOCKED);
		Assert.assertEquals(0, path.size());
	}

	@Test
	public void findPath_sticksToMaxIteration_empty() {
		finder = new AStarPathfinder<>(provider, estimator, 1);
		
		List<Point> path = finder.findPath(START, END)
				.stream()
				.map(Node::getSelf)
				.collect(Collectors.toList());

		Assert.assertEquals(0, path.size());
	}

	@Test
	public void findPath_wayExists_shortestPath() {
		List<Point> path = finder.findPath(START, END)
				.stream()
				.map(Node::getSelf)
				.collect(Collectors.toList());

		Assert.assertTrue(path.contains(new Point(0, 0)));
		Assert.assertTrue(path.contains(new Point(0, 1)));
		Assert.assertTrue(path.contains(new Point(0, 2)));
		Assert.assertEquals(3, path.size());
	}
}
