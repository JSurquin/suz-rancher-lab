# Écriture en direct : nginx, Ingress et Traefik dans kind

Cet atelier termine la partie 10 du Jour 3 matin. On écrit trois objets Kubernetes, on installe Traefik dans kind avec Helm, puis on ouvre nginx dans le navigateur en utilisant un nom local.

Le premier test utilise **http://mon-nginx.test:8080**. Une variante permet **http://mon-nginx.test**, sans port explicite, lorsque le port 80 est libre.

## Préparer le contexte et le dossier

Prérequis : Docker, kind et kubectl déjà opérationnels, le cluster kind du matin disponible (Kubernetes 1.35.8 dans le parcours), ainsi que Helm 3.18 ou plus récent. Le chart Traefik est épinglé à 42.1.3 (Traefik 3.7.16). Cet atelier n’installe pas RKE2 et n’utilise pas Gateway API.

Si vous avez fait l’atelier `publication`, terminez son nettoyage avant celui-ci. Le namespace `ingress-live`, la classe `traefik` et la release Helm de cet atelier doivent être libres. N’essayez pas de remplacer une installation déjà utilisée par d’autres exercices.

```bash
kind get clusters
kubectl config current-context
kubectl get namespace ingress-live
kubectl get ingressclass traefik
```

Le contexte doit être celui de **kind**. Les deux dernières commandes doivent répondre `NotFound` avant une première installation.

Pendant le cours, on écrit les quatre fichiers dans `~/atelier-ingress-live/`. Les fichiers de ce dépôt sont les versions complètes de référence. Pour les rejouer depuis une copie du labo public :

```bash
cd lab/kind-ingress-live
kubectl create namespace ingress-live
```

## 1. Écrire le Deployment et le Service

`deployment.yaml` décrit une copie de nginx ; le sélecteur et le modèle de Pod utilisent le label `app: mon-nginx`.

`service.yaml` fournit le nom stable `mon-nginx` et le port 80, vers le port 80 du conteneur. Son type par défaut est ClusterIP.

```bash
kubectl -n ingress-live apply -f deployment.yaml
kubectl -n ingress-live apply -f service.yaml
kubectl -n ingress-live rollout status deployment/mon-nginx --timeout=180s
kubectl -n ingress-live get pods,service
```

## 2. Écrire l’Ingress avant d’installer le contrôleur

`ingress.yaml` demande la classe `traefik`, le nom d’hôte `mon-nginx.test`, le chemin `/` et le Service `mon-nginx`, port 80.

```bash
kubectl -n ingress-live apply -f ingress.yaml
kubectl -n ingress-live get ingress
```

La règle existe dans l’API. Elle n’a pas installé Traefik ; sans contrôleur compatible, elle ne publie rien.

Le nom d’hôte d’un Ingress n’accepte pas `_`. On utilise donc `mon-nginx.test`, avec un tiret. `.test` est réservé aux tests ; on évite aussi d’introduire la résolution multicast propre à `.local` dans cet exercice.

## 3. Installer Traefik dans kind avec Helm

`values-traefik.yaml` garde le Service de Traefik en ClusterIP et désactive Gateway API pour cette petite démonstration. Le provider Ingress et la classe `traefik` sont activés par défaut dans ce chart épinglé. Pour ce chart, le type du Service se règle avec **`service.spec.type`**.

```bash
helm install traefik traefik \
  --repo https://traefik.github.io/charts --version 42.1.3 \
  --namespace ingress-live -f values-traefik.yaml \
  --wait --timeout 5m
kubectl get ingressclass traefik
kubectl -n ingress-live get pods,service,ingress
```

Le programme Traefik peut maintenant lire l’Ingress déjà enregistré. Les fichiers `deployment.yaml`, `service.yaml` et `ingress.yaml` se lisent avec kubectl ; `values-traefik.yaml` se lit avec Helm. N’appliquez pas tout le dossier avec `kubectl apply -f .`.

## 4. Ouvrir un accès au contrôleur

Choisissez **une seule** des deux configurations ci-dessous. Le terminal du transfert doit rester ouvert. Si 8080 est occupé par un ancien transfert du TP, arrêtez cet ancien transfert avec Ctrl-C, ou choisissez un autre port et adaptez les URL.

### kind et navigateur sur le même PC

```bash
kubectl -n ingress-live port-forward service/traefik 8080:80
```

Sur ce PC, ajoutez au fichier `hosts` :

```text
127.0.0.1 mon-nginx.test
```

### kind dans la VM, navigateur sur le PC

Dans la VM, sur le réseau du labo :

```bash
kubectl -n ingress-live port-forward \
  --address 0.0.0.0 service/traefik 8080:80
```

Cette commande écoute sur les interfaces de la VM. Le PC doit pouvoir joindre la VM et son port 8080 ; adaptez le réseau de la VM et le filtrage du labo si nécessaire.

Dans le fichier `hosts` **du PC du navigateur**, ajoutez l’adresse joignable de la VM. Exemple à adapter :

```text
192.168.56.20 mon-nginx.test
```

`127.0.0.1` sur le PC désigne ce PC, pas la VM distante. Un réseau NAT peut nécessiter une redirection de port ; dans ce cas, utilisez l’adresse et le port d’entrée de cette redirection.

## 5. Où modifier hosts ?

Ouvrez le fichier en administrateur sur **la machine du navigateur** :

- Linux et macOS : `/etc/hosts`.
- Windows : `C:\Windows\System32\drivers\etc\hosts`.

Ajoutez une seule association pour `mon-nginx.test`, avec l’adresse correspondant au montage choisi. Gardez les autres lignes du fichier.

Il s’agit d’une résolution locale par fichier, sans créer un enregistrement sur un serveur DNS. Le DNS interne de Kubernetes ne configure pas celui du PC.

## 6. Tester le nom dans le navigateur

Ouvrez **http://mon-nginx.test:8080** : la page nginx doit apparaître. Le navigateur doit utiliser HTTP pour ce premier test.

Dans un second terminal du PC :

```bash
curl --fail http://mon-nginx.test:8080/
curl -i -H 'Host: nom-inconnu.test' http://mon-nginx.test:8080/
```

Le premier appel doit renvoyer HTTP 200 et la page nginx. Le second doit renvoyer une **404 de Traefik** : le contrôleur est joignable, mais il ne possède pas de règle pour ce nom. C’est un accès à Traefik, pas un transfert direct vers nginx.

Pour isoler le routage de la résolution de noms, sans modifier `hosts`, on peut aussi tester localement avec :

```bash
curl --fail --resolve mon-nginx.test:8080:127.0.0.1 \
  http://mon-nginx.test:8080/
```

Cette variante suppose que le transfert tourne sur cette même machine ; avec une VM, remplacez `127.0.0.1` par son adresse accessible.

## 7. Variante : retirer :8080 de l’URL

HTTP utilise le **port 80** si aucun port n’est écrit dans l’URL. Vérifiez d’abord que le port 80 est libre sur la machine du transfert : nginx, Rancher ou un autre service peut déjà l’occuper. Dans ce cas, conservez 8080.

Arrêtez notre transfert précédent avec Ctrl-C. Sur un PC Linux/macOS qui utilise kind et son kubeconfig dans `~/.kube/config` :

```bash
sudo "$(command -v kubectl)" \
  --kubeconfig "$HOME/.kube/config" -n ingress-live \
  port-forward service/traefik 80:80
```

Dans une VM Linux hébergeant kind, utilisez à la place :

```bash
sudo "$(command -v kubectl)" \
  --kubeconfig "$HOME/.kube/config" -n ingress-live \
  port-forward --address 0.0.0.0 service/traefik 80:80
```

`sudo` donne le droit d’ouvrir le port système quand il est nécessaire ; le chemin du programme et le kubeconfig sont passés explicitement. L’entrée `hosts` reste celle du montage choisi. Vous pouvez alors ouvrir **http://mon-nginx.test**. Le pare-feu et le trajet réseau doivent autoriser le port 80 dans le cas VM.

## 8. Diagnostic et nettoyage

Si le nom ne se résout pas, vérifiez `hosts` sur le PC. Si la connexion échoue, vérifiez le transfert et son adresse d’écoute, le port, l’adresse de la VM et le réseau. Une 404 du contrôleur oriente vers le nom HTTP et la règle ; une erreur de destination demande aussi de vérifier Service et Pods.

```bash
kubectl -n ingress-live describe ingress mon-nginx
kubectl -n ingress-live get endpointslices
kubectl -n ingress-live get pods
kubectl -n ingress-live logs deployment/traefik --tail=30
```

Après la démonstration, arrêtez le transfert, vérifiez que kubectl vise toujours kind, puis retirez uniquement cet atelier :

```bash
helm uninstall traefik -n ingress-live
kubectl delete namespace ingress-live
```

Helm retire aussi sa classe Ingress. Vous pouvez enlever la ligne `hosts` ajoutée pour ce test. Gardez vos YAML. Nous conservons le cluster kind pour les autres exercices ; fermer le transfert coupe cet accès temporaire.

## Sources

- [Ingress Kubernetes : nom d’hôte et besoin d’un contrôleur](https://kubernetes.io/docs/concepts/services-networking/ingress/)
- [kubectl port-forward](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_port-forward/)
- [Chart Traefik 42.1.3 : valeurs officielles](https://github.com/traefik/traefik-helm-chart/blob/v42.1.3/traefik/values.yaml)
- [RFC 2606 : noms réservés aux tests](https://www.rfc-editor.org/rfc/rfc2606.html)

## Rejeu du 30 octobre 2026

Rejeu sur un cluster kind isolé, Kubernetes 1.35.8 : Deployment, Service et Ingress créés avant l’installation du chart Traefik 42.1.3. Le Service de Traefik est bien ClusterIP. Après installation, un transfert local sur le port de test 18082 et `curl --resolve` ont donné HTTP 200 avec la page nginx pour `mon-nginx.test`, et HTTP 404 pour un nom inconnu. Ce test ne modifie pas le fichier `hosts` du poste et ne constitue pas un test depuis une VM distante ni du port privilégié 80.
