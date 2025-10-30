# Outlaw Economy

Plugin Paper/Spigot pour offrir un système d'économie centralisé avec boutiques NPC et échanges sécurisés entre joueurs.

## Fonctionnalités
- Système d'argent persistant avec solde de départ configurable.
- Commandes `/balance`, `/pay`, `/trade`, `/shop`.
- Boutiques NPC pour vendre et acheter des items (outils, blocs, nourriture).
- Magasin général commun où les joueurs peuvent vendre leurs objets, 30 annonces par page et paiement direct au vendeur.
- Échanges joueur à joueur avec interface dédiée permettant d'offrir items et argent.
- API publique pour que vos autres plugins (Clans, territoires, etc.) puissent manipuler l'argent.

## Installation
1. Compiler le plugin : `mvn package` (Java 17+ requis).
2. Déposer le fichier `target/outlaw-economy-1.0.0-SNAPSHOT.jar` dans le dossier `plugins` de votre serveur Paper/Spigot 1.20.4.
3. Démarrer le serveur afin de générer les fichiers de configuration (`config.yml`, `balances.yml`, `shops.yml`).

## Commandes

### Sans permission (tous les joueurs)
| Commande | Description |
| --- | --- |
| `/balance` | Affiche le solde du joueur. |
| `/pay <joueur> <montant>` | Transfert d'argent entre joueurs. |
| `/trade <joueur>` | Envoie une demande d'échange. Sous-commandes : `accept`, `deny`, `cancel`. |

### Joueurs avec la permission `outlaweco.use`
| Commande | Description |
| --- | --- |
| `/shop open <template>` | Ouvre une boutique PNJ à partir d'un template configuré. |
| `/shop open general` | Ouvre le magasin général commun (alias : `/shop open shop general`). |

### Administrateurs (`outlawecoadmin`)
| Commande | Description |
| --- | --- |
| `/shop create <template>` | Crée une boutique PNJ. |
| `/shop remove` | Supprime la boutique ciblée. |
| `/shop list [templates]` | Liste les boutiques créées ou les templates disponibles. |
| `/shop add itemshop <template> <item> <quantité> <prixAchat> [prixVente]` | Ajoute un objet à un template simple. |
| `/shop removeitem <template> <item>` | Retire un objet d'un template simple. |
| `/shop reloadtemplates` | Recharge les templates depuis le fichier YAML. |
| `/shop setting price` | Ouvre le menu de définition des prix pour les items. |

## Configuration (`config.yml`)
```yaml
economy:
  starting-balance: 100.0   # argent initial pour les nouveaux joueurs
  currency-name: "OutlawCoin"
trade:
  request-timeout: 30       # durée (secondes) des demandes d'échange
```

## API d'économie pour vos plugins
Deux approches sont possibles pour interagir avec l'économie depuis un autre plugin.

### 1. Utiliser l'API statique
```java
import com.outlaweco.api.EconomyAPI;

UUID joueur = ...;
EconomyAPI.deposit(joueur, 250.0);          // ajoute 250 OutlawCoin
boolean ok = EconomyAPI.withdraw(joueur, 50.0); // retire 50 (false si solde insuffisant)
double balance = EconomyAPI.getBalance(joueur);
```

### 2. Via le ServicesManager de Bukkit
```java
import com.outlaweco.api.EconomyService;
import org.bukkit.Bukkit;

EconomyService economy = Bukkit.getServicesManager().load(EconomyService.class);
if (economy != null) {
    UUID joueur = ...;
    if (economy.has(joueur, 500.0)) {
        economy.withdraw(joueur, 500.0);
        // ... effectuer l'achat d'un territoire, etc.
    }
}
```

L'interface `EconomyService` expose les méthodes suivantes :
- `double getBalance(UUID joueur)`
- `void setBalance(UUID joueur, double montant)`
- `void deposit(UUID joueur, double montant)`
- `boolean withdraw(UUID joueur, double montant)`
- `boolean has(UUID joueur, double montant)`

Assurez-vous que votre plugin déclare une dépendance vers OutlawEconomy (via `plugin.yml` ou `softdepend`) pour être chargé après celui-ci.

## Création de boutiques NPC
- Les boutiques sont basées sur des villageois invulnérables.
- Effectuez `/shop create <type>` à l'endroit souhaité pour placer le NPC.
- Les joueurs interagissent avec le NPC pour ouvrir le menu d'achat/vente. Clic gauche = achat, clic droit = vente.
- Les boutiques sont sauvegardées dans `plugins/OutlawEconomy/shops.yml`.

## Système d'échange
- `/trade <joueur>` envoie une demande, qui expire après la durée définie dans la config.
- Lorsque l'échange est accepté, une interface s'ouvre avec deux colonnes : chaque joueur dépose ses items.
- Les boutons en bas permettent de proposer de l'argent (+1, +10, +100) et de confirmer l'échange.
- Si les deux joueurs confirment et possèdent les fonds nécessaires, l'échange est validé.

## Persistance des données
- `balances.yml` stocke les soldes des joueurs.
- `shops.yml` enregistre l'emplacement et le type de chaque boutique.

## Développement
- Langage : Java 17
- Build : Maven
- API serveur : Paper 1.20.4
