import { Link, NavLink, Outlet } from 'react-router'
import { BellIcon, PlusIcon, SearchIcon, UserIcon } from '../../shared/ui/icons'
import styles from './AppLayout.module.css'
import { NAV_ITEMS } from './navItems'

const COMING_SOON = 'Disponível a partir da Fase 1'

/**
 * Estrutura comum a todas as telas: cabeçalho com a área global, menu principal e o conteúdo da rota.
 *
 * No desktop o menu fica na lateral; no celular vira uma barra fixa no rodapé, ao alcance do polegar
 * (RN-UX-06). Os controles da área global ainda não têm funcionalidade e aparecem desabilitados, com o
 * motivo no `title`, em vez de parecerem funcionar e não fazerem nada.
 */
export function AppLayout() {
  return (
    <div className={styles.shell}>
      <a href="#conteudo" className={styles.skipLink}>
        Pular para o conteúdo
      </a>

      <header className={styles.header}>
        <Link to="/" className={styles.brand}>
          <span className={styles.brandMark} aria-hidden="true" />
          Shared Calendar
        </Link>

        <div className={styles.globalArea}>
          <label className={styles.search} title={COMING_SOON}>
            <SearchIcon />
            <span className="visually-hidden">Pesquisar</span>
            <input type="search" placeholder="Pesquisar" disabled />
          </label>

          <button type="button" className={styles.createButton} disabled title={COMING_SOON}>
            <PlusIcon />
            <span>Criar</span>
          </button>

          <button type="button" className={styles.iconButton} disabled title={COMING_SOON}>
            <BellIcon />
            <span className="visually-hidden">Notificações</span>
          </button>

          <button type="button" className={styles.iconButton} disabled title={COMING_SOON}>
            <UserIcon />
            <span className="visually-hidden">Perfil</span>
          </button>
        </div>
      </header>

      <nav className={styles.nav} aria-label="Menu principal">
        <ul className={styles.navList}>
          {NAV_ITEMS.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  isActive ? `${styles.navLink} ${styles.navLinkActive}` : styles.navLink
                }
              >
                {item.icon}
                <span>{item.label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <main id="conteudo" className={styles.main} tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
