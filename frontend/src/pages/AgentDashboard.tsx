import { useState } from 'react';
import AgentNav from '@/components/Agent/AgentNav';
import TicketList from '@/components/Agent/TicketList';
import TicketDetail from '@/components/Agent/TicketDetail';
import AgentWorkspace from '@/components/Agent/AgentWorkspace';
import AdminPanel from '@/components/Agent/AdminPanel';
import type { TicketItem } from '@/api/types';

export type View = 'tickets' | 'detail' | 'workspace' | 'admin';

export default function AgentDashboard() {
  const [view, setView] = useState<View>('tickets');
  const [selectedTicket, setSelectedTicket] = useState<TicketItem | null>(null);

  const openDetail = (ticket: TicketItem) => {
    setSelectedTicket(ticket);
    setView('detail');
  };

  const backToList = () => {
    setSelectedTicket(null);
    setView('tickets');
  };

  return (
    <div className="flex-1 flex min-w-0">
      <AgentNav currentView={view} onNavigate={setView} />
      <div className="flex-1 overflow-auto">
        {view === 'tickets' && <TicketList onOpenTicket={openDetail} />}
        {view === 'detail' && selectedTicket && (
          <TicketDetail ticket={selectedTicket} onBack={backToList} onUpdated={backToList} />
        )}
        {view === 'workspace' && <AgentWorkspace />}
        {view === 'admin' && <AdminPanel />}
      </div>
    </div>
  );
}
