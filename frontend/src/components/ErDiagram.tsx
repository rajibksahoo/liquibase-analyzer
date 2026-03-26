import { useCallback, useMemo, useRef, useState } from 'react';
import {
  ReactFlow,
  Controls,
  MiniMap,
  Background,
  BackgroundVariant,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import TableNode from './TableNode';
import RelationshipEdge from './RelationshipEdge';
import DiagramToolbar from './DiagramToolbar';
import type { DiagramData, ErNodeData } from '../types';

const nodeTypes = { tableNode: TableNode };
const edgeTypes = { relationshipEdge: RelationshipEdge };

interface HighlightedFk {
  edgeId: string;
  sourceNodeId: string;
  sourceColumn: string;
  targetNodeId: string;
  targetColumn: string;
}

interface Props {
  diagramData: DiagramData;
}

function nodeMatchesSearch(data: Record<string, unknown>, query: string): boolean {
  if (!query) return true;
  const q = query.toLowerCase();
  const nodeData = data as unknown as ErNodeData;
  if (nodeData.tableName.toLowerCase().includes(q)) return true;
  return (nodeData.columns as ErNodeData['columns']).some((col) =>
    col.name.toLowerCase().includes(q)
  );
}

export default function ErDiagram({ diagramData }: Props) {
  const [searchQuery, setSearchQuery] = useState('');
  const [highlightedFk, setHighlightedFk] = useState<HighlightedFk | null>(null);

  const onFkClick = useCallback(
    (nodeId: string, columnName: string) => {
      // If clicking the same FK, toggle off
      if (highlightedFk?.sourceNodeId === nodeId && highlightedFk?.sourceColumn === columnName) {
        setHighlightedFk(null);
        return;
      }
      // Find matching edge where this node+column is the source
      const edge = diagramData.edges.find(
        (e) => e.source === nodeId && e.data.sourceColumn === columnName
      );
      if (edge) {
        setHighlightedFk({
          edgeId: edge.id,
          sourceNodeId: edge.source,
          sourceColumn: edge.data.sourceColumn,
          targetNodeId: edge.target,
          targetColumn: edge.data.targetColumn,
        });
      }
    },
    [diagramData.edges, highlightedFk]
  );

  const onPaneClick = useCallback(() => {
    setHighlightedFk(null);
  }, []);

  const initialNodes = useMemo(
    () =>
      diagramData.nodes.map((n) => ({
        id: n.id,
        type: n.type,
        position: n.position,
        data: n.data as Record<string, unknown>,
      })),
    [diagramData.nodes]
  );

  const initialEdges = useMemo(
    () =>
      diagramData.edges.map((e) => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle,
        type: e.type,
        data: e.data as Record<string, unknown>,
      })),
    [diagramData.edges]
  );

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const rfInstance = useRef<any>(null);

  // Build a set of matching node IDs for search
  const matchingNodeIds = useMemo(() => {
    if (!searchQuery) return null;
    const ids = new Set<string>();
    for (const n of initialNodes) {
      if (nodeMatchesSearch(n.data, searchQuery)) {
        ids.add(n.id);
      }
    }
    return ids;
  }, [searchQuery, initialNodes]);

  // Inject searchQuery, highlightedFk, and onFkClick into node data
  const enrichedNodes = useMemo(
    () =>
      nodes.map((n) => ({
        ...n,
        data: {
          ...n.data,
          searchQuery,
          highlightedFk,
          onFkClick,
        },
      })),
    [nodes, searchQuery, highlightedFk, onFkClick]
  );

  // Inject highlight/dim info into edge data
  const enrichedEdges = useMemo(
    () =>
      edges.map((e) => {
        const isHighlighted = highlightedFk?.edgeId === e.id;
        const isDimmedBySearch =
          matchingNodeIds !== null &&
          !matchingNodeIds.has(e.source) &&
          !matchingNodeIds.has(e.target);
        return {
          ...e,
          data: {
            ...e.data,
            highlighted: isHighlighted,
            dimmed: isDimmedBySearch,
          },
        };
      }),
    [edges, highlightedFk, matchingNodeIds]
  );

  const onInit = useCallback((instance: any) => {
    rfInstance.current = instance;
    setTimeout(() => instance.fitView({ padding: 0.1 }), 100);
  }, []);

  const onFitView = useCallback(() => rfInstance.current?.fitView({ padding: 0.1 }), []);
  const onZoomIn = useCallback(() => rfInstance.current?.zoomIn(), []);
  const onZoomOut = useCallback(() => rfInstance.current?.zoomOut(), []);

  return (
    <div className="diagram-container">
      <DiagramToolbar
        snapshotName={diagramData.snapshotName}
        nodeCount={diagramData.nodes.length}
        edgeCount={diagramData.edges.length}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        onFitView={onFitView}
        onZoomIn={onZoomIn}
        onZoomOut={onZoomOut}
      />
      <div className="diagram-canvas">
        <ReactFlow
          nodes={enrichedNodes}
          edges={enrichedEdges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          onInit={onInit}
          onPaneClick={onPaneClick}
          fitView
          minZoom={0.1}
          maxZoom={2}
          defaultEdgeOptions={{ animated: false }}
        >
          <Controls position="bottom-right" />
          <MiniMap
            nodeStrokeWidth={3}
            pannable
            zoomable
            style={{ border: '1px solid var(--color-border)' }}
          />
          <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="var(--color-diagram-dots)" />
        </ReactFlow>
      </div>
    </div>
  );
}
