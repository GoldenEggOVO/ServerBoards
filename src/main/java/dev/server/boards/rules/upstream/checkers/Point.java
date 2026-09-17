// Adapted from Stephane Coutant, GPL-3.0-or-later; see META-INF/licenses.
package dev.server.boards.rules.upstream.checkers;

public class Point implements dev.server.boards.rules.upstream.checkers.Serializable {
	public int i;
	public int j;
	
	public Point(int i, int j){
		this.i = i ;
		this.j = j;
	}
	public Point(Point p){
		this( p.i, p.j);
	}
	public Point(Peg peg){
		this(peg.point);
	}
	public Point clone() {
		return new Point(i,j);
	}
	
	public String toString(){
		return String.format("(%d, %d)", i, j);
	}
	
	/** --> */ 
	public void increment() {
		if ( odd(j)) i++;
	}
	/** <-- */ 
	public void decrement() {
		if ( even(j)) i--;
	}
	
	private static boolean odd(int value) {
		return (value % 2 > 0);
	}
	private static boolean even(int value) {
		return (value % 2 == 0);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Point other)) return false;
		return (i==other.i) && (j==other.j);
	}
	
	@Override public int hashCode() { return 31 * i + j; }

	public boolean isOdd() {
		return j%2 == 1; 
	}
	public boolean isEven() {
		return j%2 == 0; 
	}
	@Override
	public String serialize() {
		return String.format( "%d,%d", i, j);
	}
	public static Point desialize(String str) {
		if (str==null || str.length()==0) return null;
		String[] data = str.split(",");
		if (data.length!=2) throw new IllegalArgumentException("Point shall comply to format : 'i,j' ");
		int i = Integer.valueOf(data[0]);
		int j = Integer.valueOf(data[1]);
		return new Point(i,j);
	}
}

///** @return a representation of the peg */
//public static String serialize(Point p) {
//	return String.format( ":%d:%d", p.i, p.j);
//}
