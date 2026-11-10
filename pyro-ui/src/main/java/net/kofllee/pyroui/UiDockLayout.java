package net.kofllee.pyroui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One planar workspace, composed of independent rectangular zones rather than
 * recursive binary splits. Coordinates are normalized against the work area;
 * T-junctions and multi-zone aligned edges are first-class.
 *
 * Layout operations work on copies until drop, so the preview and committed
 * placement are bit-for-bit the same when projected into the same viewport.
 */
final class UiDockLayout {
    enum Side { CENTER, LEFT, RIGHT, TOP, BOTTOM, OUTER_LEFT, OUTER_RIGHT, OUTER_TOP, OUTER_BOTTOM }
    static final int GAP = 6;
    private static final double EPS = 1e-7;

    static final class Box {
        double x0,y0,x1,y1;
        Box(double x0,double y0,double x1,double y1) {
            this.x0=x0;this.y0=y0;this.x1=x1;this.y1=y1;
        }
        Box copy() {return new Box(x0,y0,x1,y1);}
        double width() {return x1-x0;}
        double height() {return y1-y0;}
    }
    private final LinkedHashMap<String,Box> zones = new LinkedHashMap<>();
    UiDockLayout copy() {
        var out=new UiDockLayout();
        zones.forEach((key,value)->out.zones.put(key,value.copy()));
        return out;
    }
    Map<String,Box> zones() {return java.util.Collections.unmodifiableMap(zones);}
    Box get(String id) {return zones.get(id);}
    void put(String id,Box box) {zones.put(id,box);}
    void clear() {zones.clear();}
    UiRect rect(String id,UiRect workspace) {
        Box b=zones.get(id);
        if(b==null)return null;
        int left=workspace.x()+(int)Math.round(b.x0*workspace.w())+(b.x0>EPS?GAP/2:0);
        int right=workspace.x()+(int)Math.round(b.x1*workspace.w())-(b.x1<1-EPS?GAP/2:0);
        int top=workspace.y()+(int)Math.round(b.y0*workspace.h())+(b.y0>EPS?GAP/2:0);
        int bottom=workspace.y()+(int)Math.round(b.y1*workspace.h())-(b.y1<1-EPS?GAP/2:0);
        return new UiRect(left,top,Math.max(0,right-left),Math.max(0,bottom-top));
    }

    boolean insert(String id,String target,Side side,double preferred) {
        if(zones.containsKey(id))return false;
        if(zones.isEmpty()) {
            zones.put(id,new Box(0,0,1,1));
            return true;
        }
        if(side==Side.CENTER)return zones.containsKey(target);
        // Narrow zones sometimes need a new pane to occupy >45% just to
        // satisfy its minimum width. Reject impossible geometry in the
        // manager's per-window minimum checks, not via a universal ratio cap.
        double fraction=Math.max(.06,Math.min(
                side.name().startsWith("OUTER_")?.45:.85,preferred));
        Box box;
        if(side.name().startsWith("OUTER_")) {
            if(side==Side.OUTER_LEFT || side==Side.OUTER_RIGHT) {
                for(Box existing:zones.values()) {
                    if(side==Side.OUTER_LEFT) {
                        existing.x0=fraction+(1-fraction)*existing.x0;
                        existing.x1=fraction+(1-fraction)*existing.x1;
                    } else {
                        existing.x0=(1-fraction)*existing.x0;
                        existing.x1=(1-fraction)*existing.x1;
                    }
                }
                box=side==Side.OUTER_LEFT?new Box(0,0,fraction,1):
                        new Box(1-fraction,0,1,1);
            } else {
                for(Box existing:zones.values()) {
                    if(side==Side.OUTER_TOP) {
                        existing.y0=fraction+(1-fraction)*existing.y0;
                        existing.y1=fraction+(1-fraction)*existing.y1;
                    } else {
                        existing.y0=(1-fraction)*existing.y0;
                        existing.y1=(1-fraction)*existing.y1;
                    }
                }
                box=side==Side.OUTER_TOP?new Box(0,0,1,fraction):
                        new Box(0,1-fraction,1,1);
            }
        } else {
            Box old=zones.get(target);
            if(old==null)return false;
            box=old.copy();
            switch(side) {
                case LEFT -> {double cut=old.x0+old.width()*fraction;box.x1=cut;old.x0=cut;}
                case RIGHT -> {double cut=old.x1-old.width()*fraction;box.x0=cut;old.x1=cut;}
                case TOP -> {double cut=old.y0+old.height()*fraction;box.y1=cut;old.y0=cut;}
                case BOTTOM -> {double cut=old.y1-old.height()*fraction;box.y0=cut;old.y1=cut;}
                default -> throw new IllegalArgumentException("Invalid local insertion");
            }
        }
        zones.put(id,box);
        return true;
    }

    /**
     * Merge an orphaned rectangle into a full-length adjacent rectangle, or
     * into all neighbors that exactly tile its shared edge. This avoids making
     * any L-shaped zone and leaves no holes in the workspace.
     */
    boolean remove(String key) {
        Box removed=zones.get(key);
        if(removed==null)return false;
        if(zones.size()==1) {zones.remove(key);return true;}
        // Prefer one matching neighbor; it avoids resizing unrelated zones.
        for(var entry:zones.entrySet()) {
            if(entry.getKey().equals(key))continue;
            Box b=entry.getValue();
            if(equal(b.y0,removed.y0)&&equal(b.y1,removed.y1)) {
                if(equal(b.x1,removed.x0)||equal(b.x0,removed.x1)) {
                    b.x0=Math.min(b.x0,removed.x0);b.x1=Math.max(b.x1,removed.x1);
                    zones.remove(key);return true;
                }
            }
            if(equal(b.x0,removed.x0)&&equal(b.x1,removed.x1)) {
                if(equal(b.y1,removed.y0)||equal(b.y0,removed.y1)) {
                    b.y0=Math.min(b.y0,removed.y0);b.y1=Math.max(b.y1,removed.y1);
                    zones.remove(key);return true;
                }
            }
        }
        // A large zone can border several smaller zones with the same
        // straight edge; all can expand into its former space.
        for(int side=0;side<4;side++) {
            List<Box> neighbors=new ArrayList<>();
            for(var entry:zones.entrySet()) {
                if(entry.getKey().equals(key))continue;
                Box b=entry.getValue();
                boolean adjacent=switch(side) {
                    case 0 -> equal(b.x1,removed.x0) && b.y0>=removed.y0-EPS && b.y1<=removed.y1+EPS;
                    case 1 -> equal(b.x0,removed.x1) && b.y0>=removed.y0-EPS && b.y1<=removed.y1+EPS;
                    case 2 -> equal(b.y1,removed.y0) && b.x0>=removed.x0-EPS && b.x1<=removed.x1+EPS;
                    default -> equal(b.y0,removed.y1) && b.x0>=removed.x0-EPS && b.x1<=removed.x1+EPS;
                };
                if(adjacent)neighbors.add(b);
            }
            if(neighbors.isEmpty())continue;
            final boolean vertical=side<2;
            neighbors.sort(Comparator.comparingDouble(b->vertical?b.y0:b.x0));
            double cursor=vertical?removed.y0:removed.x0;
            boolean complete=true;
            for(Box b:neighbors) {
                double a=vertical?b.y0:b.x0, c=vertical?b.y1:b.x1;
                if(!equal(a,cursor)) {complete=false;break;}
                cursor=c;
            }
            if(!complete || !equal(cursor,vertical?removed.y1:removed.x1))continue;
            for(Box b:neighbors) {
                switch(side) {
                    case 0 -> b.x1=removed.x1;
                    case 1 -> b.x0=removed.x0;
                    case 2 -> b.y1=removed.y1;
                    default -> b.y0=removed.y0;
                }
            }
            zones.remove(key);return true;
        }
        // Never leave a hole; let the empty zone remain available for
        // subsequent docking rather than create a non-rectangular panel.
        return false;
    }

    record Edge(boolean vertical,double position,List<String> before,List<String> after,
                double begin,double end) {}

    /** Only a one-to-one, equally long seam is a symmetric drop target.
     * A T-junction can still be resized as a coupled edge, but docking onto
     * it should split the approached panel rather than move the whole row.
     */
    boolean symmetricDropEdge(Edge edge) {
        if(edge==null || edge.before().size()!=1 || edge.after().size()!=1)return false;
        Box a=zones.get(edge.before().getFirst()),b=zones.get(edge.after().getFirst());
        if(a==null||b==null)return false;
        return edge.vertical()
                ?equal(a.y0,b.y0)&&equal(a.y1,b.y1)
                :equal(a.x0,b.x0)&&equal(a.x1,b.x1);
    }
    /** A local side with a matching one-to-one neighbor is NOT a local dock
     * target. Both sides of that full seam resolve to the identical Edge,
     * regardless of how far the pointer is from the divider line.
     */
    Edge matchingEdgeForZoneSide(String zone,Side side) {
        Box box=zones.get(zone);
        if(box==null)return null;
        for(var entry:zones.entrySet()) {
            if(entry.getKey().equals(zone))continue;
            String otherId=entry.getKey();
            Box other=entry.getValue();
            switch(side) {
                case LEFT -> {
                    if(equal(other.x1,box.x0) &&
                            equal(other.y0,box.y0) && equal(other.y1,box.y1))
                        return new Edge(true,box.x0,List.of(otherId),List.of(zone),
                                box.y0,box.y1);
                }
                case RIGHT -> {
                    if(equal(box.x1,other.x0) &&
                            equal(box.y0,other.y0) && equal(box.y1,other.y1))
                        return new Edge(true,box.x1,List.of(zone),List.of(otherId),
                                box.y0,box.y1);
                }
                case TOP -> {
                    if(equal(other.y1,box.y0) &&
                            equal(other.x0,box.x0) && equal(other.x1,box.x1))
                        return new Edge(false,box.y0,List.of(otherId),List.of(zone),
                                box.x0,box.x1);
                }
                case BOTTOM -> {
                    if(equal(box.y1,other.y0) &&
                            equal(box.x0,other.x0) && equal(box.x1,other.x1))
                        return new Edge(false,box.y1,List.of(zone),List.of(otherId),
                                box.x0,box.x1);
                }
                default -> {}
            }
        }
        return null;
    }
    /** Find a pair sharing the exact same seam, independently of T-junction
     * edge discovery. Such a pair always owns a single two-sided drop target.
     */
    Edge matchingDropEdgeAt(double mx,double my,UiRect area,double reach) {
        double best=Double.POSITIVE_INFINITY;
        Edge found=null;
        for(var left:zones.entrySet())for(var right:zones.entrySet()) {
            if(left==right)continue;
            Box a=left.getValue(),b=right.getValue();
            for(boolean vertical:new boolean[]{true,false}) {
                boolean adjacent=vertical
                        ?equal(a.x1,b.x0)&&equal(a.y0,b.y0)&&equal(a.y1,b.y1)
                        :equal(a.y1,b.y0)&&equal(a.x0,b.x0)&&equal(a.x1,b.x1);
                if(!adjacent)continue;
                double p=vertical?a.x1:a.y1;
                double begin=vertical?a.y0:a.x0,end=vertical?a.y1:a.x1;
                double cross=vertical?(my-area.y())/Math.max(1,area.h())
                        :(mx-area.x())/Math.max(1,area.w());
                double distance=Math.abs(vertical
                        ?mx-(area.x()+p*area.w()):my-(area.y()+p*area.h()));
                // The two halves of one continuous seam are ONE drop zone,
                // not two competing near-edge sectors. Let the seam own the
                // nearby sector on both sides, up to 32% of the narrower pane.
                double smallerSpan=vertical?Math.min(a.width(),b.width())*area.w()
                        :Math.min(a.height(),b.height())*area.h();
                double symmetricReach=Math.max(reach,Math.min(120,smallerSpan*.32));
                if(cross<begin+EPS || cross>end-EPS || distance>symmetricReach ||
                        distance>=best)continue;
                best=distance;
                found=new Edge(vertical,p,List.of(left.getKey()),
                        List.of(right.getKey()),begin,end);
            }
        }
        return found;
    }

    /**
     * Insert an independent rectangle directly along a shared boundary,
     * including T-junctions where one side contains several zones.
     *
     * Center the new panel on the matching boundary. If one neighbor lacks
     * space for its half, take the remainder from the other neighbor.
     * Every zone touching this straight segment is resized by the same amount
     * and no recursive split hierarchy is created.
     */
    boolean insertOnEdge(String id,Edge edge,boolean preferBefore,
                         double size,double beforeCapacity,double afterCapacity) {
        if(zones.containsKey(id)||edge==null||size<=EPS)return false;
        if(beforeCapacity+afterCapacity+EPS<size)return false;
        double before=Math.min(size/2,beforeCapacity);
        double after=size-before;
        if(after>afterCapacity) {
            after=afterCapacity;
            before=size-after;
        }
        double p=edge.position();
        Box region=edge.vertical()
                ?new Box(p-before,edge.begin(),p+after,edge.end())
                :new Box(edge.begin(),p-before,edge.end(),p+after);
        if(region.width()<=EPS||region.height()<=EPS)return false;
        for(String key:edge.before()) {
            Box box=zones.get(key);
            if(box==null)return false;
            if(edge.vertical())box.x1=p-before;
            else box.y1=p-before;
        }
        for(String key:edge.after()) {
            Box box=zones.get(key);
            if(box==null)return false;
            if(edge.vertical())box.x0=p+after;
            else box.y0=p+after;
        }
        zones.put(id,region);
        return coversWorkspace();
    }
    Edge edgeAt(double mx,double my,UiRect work,double tolerance) {
        double x=(mx-work.x())/Math.max(1,work.w());
        double y=(my-work.y())/Math.max(1,work.h());
        Edge best=null;
        double bestDistance=Double.POSITIVE_INFINITY;
        for(var e:zones.entrySet()) {
            Box a=e.getValue();
            for(boolean vertical:new boolean[]{true,false}) {
                double p=vertical?a.x1:a.y1,coord=vertical?x:y;
                double distance=Math.abs((p-coord)*(vertical?work.w():work.h()));
                if(distance>tolerance || p<=EPS || p>=1-EPS || distance>=bestDistance)continue;
                double cross=vertical?y:x;
                if(cross<a.y0-EPS && vertical || cross>a.y1+EPS && vertical)continue;
                if(!vertical && (cross<a.x0-EPS||cross>a.x1+EPS))continue;
                Edge candidate=connectedEdge(vertical,p,cross);
                if(candidate!=null) {best=candidate;bestDistance=distance;}
            }
        }
        return best;
    }
    private Edge connectedEdge(boolean vertical,double boundary,double coordinate) {
        List<Map.Entry<String,Box>> left=new ArrayList<>(),right=new ArrayList<>();
        for(var e:zones.entrySet()) {
            Box b=e.getValue();
            if(equal(vertical?b.x1:b.y1,boundary))left.add(e);
            if(equal(vertical?b.x0:b.y0,boundary))right.add(e);
        }
        if(left.isEmpty()||right.isEmpty())return null;
        double begin=coordinate,end=coordinate;
        boolean changed=true;
        List<String> first=new ArrayList<>(),second=new ArrayList<>();
        while(changed) {
            changed=false;
            for(var e:left) {
                Box b=e.getValue();
                double from=vertical?b.y0:b.x0,to=vertical?b.y1:b.x1;
                if(overlapsSegment(from,to,begin,end,coordinate) &&
                        !first.contains(e.getKey())) {
                    first.add(e.getKey());begin=Math.min(begin,from);end=Math.max(end,to);changed=true;
                }
            }
            for(var e:right) {
                Box b=e.getValue();
                double from=vertical?b.y0:b.x0,to=vertical?b.y1:b.x1;
                if(overlapsSegment(from,to,begin,end,coordinate) &&
                        !second.contains(e.getKey())) {
                    second.add(e.getKey());begin=Math.min(begin,from);end=Math.max(end,to);changed=true;
                }
            }
        }
        if(first.isEmpty()||second.isEmpty())return null;
        // A two-sided divider must be completely shared for its entire
        // length. Overlapping staggered edges can otherwise drag unrelated
        // windows or create a hole where only one neighbor was resized.
        if(!tilesEdge(left,first,vertical,begin,end) ||
                !tilesEdge(right,second,vertical,begin,end))return null;
        return new Edge(vertical,boundary,List.copyOf(first),List.copyOf(second),begin,end);
    }
    private static boolean tilesEdge(List<Map.Entry<String,Box>> candidates,
                                     List<String> selected,boolean vertical,
                                     double begin,double end) {
        List<Box> boxes=new ArrayList<>();
        for(var entry:candidates)if(selected.contains(entry.getKey()))boxes.add(entry.getValue());
        boxes.sort(Comparator.comparingDouble(b->vertical?b.y0:b.x0));
        double cursor=begin;
        for(Box box:boxes) {
            double from=vertical?box.y0:box.x0;
            double to=vertical?box.y1:box.x1;
            if(!equal(from,cursor))return false;
            cursor=to;
        }
        return equal(cursor,end);
    }
    private static boolean overlapsSegment(double from,double to,double begin,double end,
                                           double pointer) {
        if(end-begin<EPS)return pointer>from+EPS && pointer<to-EPS;
        // Touching at one endpoint must NOT merge two independent borders
        // into a new global splitter. Only positive-length overlap counts.
        return Math.min(to,end)-Math.max(from,begin)>EPS;
    }
    double resize(Edge edge,double requested,double minimumBefore,double minimumAfter) {
        if(edge==null)return 0;
        double low=-Double.MAX_VALUE,high=Double.MAX_VALUE;
        for(String key:edge.before) {
            Box b=zones.get(key);
            if(b==null)return 0;
            low=Math.max(low,(edge.vertical?b.x0:b.y0)+minimumBefore-edge.position);
        }
        for(String key:edge.after) {
            Box b=zones.get(key);
            if(b==null)return 0;
            high=Math.min(high,(edge.vertical?b.x1:b.y1)-minimumAfter-edge.position);
        }
        if(low>high)return 0;
        double delta=Math.max(low,Math.min(high,requested));
        if(!Double.isFinite(delta))return 0;
        for(String key:edge.before) {
            Box b=zones.get(key);
            if(edge.vertical)b.x1=edge.position+delta;else b.y1=edge.position+delta;
        }
        for(String key:edge.after) {
            Box b=zones.get(key);
            if(edge.vertical)b.x0=edge.position+delta;else b.y0=edge.position+delta;
        }
        return delta;
    }

    boolean coversWorkspace() {
        if(zones.isEmpty())return true;
        var xs=new java.util.TreeSet<Double>();
        var ys=new java.util.TreeSet<Double>();
        xs.add(0d);xs.add(1d);ys.add(0d);ys.add(1d);
        for(Box b:zones.values()) {
            if(b.width()<=EPS||b.height()<=EPS||b.x0<-EPS||b.y0<-EPS||b.x1>1+EPS||b.y1>1+EPS)return false;
            xs.add(b.x0);xs.add(b.x1);ys.add(b.y0);ys.add(b.y1);
        }
        var x=new ArrayList<>(xs);var y=new ArrayList<>(ys);
        for(int ix=0;ix<x.size()-1;ix++)for(int iy=0;iy<y.size()-1;iy++) {
            double px=(x.get(ix)+x.get(ix+1))/2,py=(y.get(iy)+y.get(iy+1))/2;
            int count=0;
            for(Box b:zones.values())if(px>b.x0+EPS&&px<b.x1-EPS&&py>b.y0+EPS&&py<b.y1-EPS)count++;
            if(count!=1)return false;
        }
        return true;
    }
    private static boolean equal(double a,double b) {return Math.abs(a-b)<EPS;}
}
