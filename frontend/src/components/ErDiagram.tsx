import { useCallback, useMemo } from 'react';
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
import type { DiagramData } from '../types';

const nodeTypes = { tableNode: TableNode };
const edgeTypes = { relationshipEdge: RelationshipEdge };

interface Props {
  diagramData: DiagramData;
}

export default function ErDiagram({ diagramData }: Props) {
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

  const [nodes, , onNodesChange] = useNodesState(initialNodes);
  const [edges, , onEdgesChange] = useEdgesState(initialEdges);

  const onInit = useCallback((instance: any) => {
    setTimeout(() => instance.fitView({ padding: 0.1 }), 100);
  }, []);

  return (
    <div className="diagram-container">
      <DiagramToolbar
        snapshotName={diagramData.snapshotName}
        nodeCount={diagramData.nodes.length}
        edgeCount={diagramData.edges.length}
      />
      <div className="diagram-canvas">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          onInit={onInit}
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
          <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="#e2e8f0" />
        </ReactFlow>
      </div>
    </div>
  );
}
